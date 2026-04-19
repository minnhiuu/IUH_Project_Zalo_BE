from fastapi import APIRouter, Depends, HTTPException, BackgroundTasks
from fastapi.responses import StreamingResponse
from app.service.graph_manager import get_compiled_graph
from app.graph import edges
from app.security.security_context import security_context_dependency, user_context
from app.messaging.kafka_producer import send_event, send_socket_event
from app.messaging.event_types import KafkaEventType
from app.config.app_config import settings
from app.config.constants import BONDHUB_AI_ID
from app.dto.request.chat_request import ChatRequest
from app.dto.request.summary_request import SummaryRequest
from app.dto.response.summary_response import SummaryResponse
from app.service.ai_service import summarize_messages, summarize_messages_stream
from app.client.message_client import get_recent_messages
from app.utils.string_utils import sanitize_ai_query
from langchain_core.messages import HumanMessage
import json
import logging
import datetime

router = APIRouter()
logger = logging.getLogger(__name__)

async def persist_ai_response(conversation_id: str, content: str, user_id: str, is_mention: bool):
    """Gửi tin nhắn AI hoàn chỉnh qua Kafka để Java lưu DB và broadcast WebSocket."""
    if not content:
        return
    
    save_event = {
        "chatId": conversation_id,
        "senderId": BONDHUB_AI_ID,
        "senderName": "Bondhub AI",
        "content": content,
        "type": "CHAT",
        "userId": user_id
    }
    await send_event(settings.ai_message_save_topic, save_event, KafkaEventType.AI_MESSAGE_SAVE)
    logger.info(f"--- [KAFKA PERSIST] Sent AI response for chat {conversation_id} (mention={is_mention}) content_len={len(content)} ---")

@router.post("/summarize")
async def summarize(req: SummaryRequest, user_info: dict = Depends(security_context_dependency)):
    async def event_generator():
        user_context.set(user_info)
        async for token in summarize_messages_stream(req.conversationId, req.sinceMessageId):
            yield f"data: {json.dumps({'content': token})}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "X-Accel-Buffering": "no",
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )

@router.post("/chat")
async def chat(
    chat_req: ChatRequest, 
    background_tasks: BackgroundTasks, 
    user_info: dict = Depends(security_context_dependency)
):
    raw_query = chat_req.query or chat_req.content
    if not raw_query:
        raise HTTPException(status_code=400, detail="query or content is required")

    clean_query = sanitize_ai_query(raw_query)
    conversation_id = chat_req.conversationId
    is_mention = chat_req.isMention
    user_id = user_info.get("user_id")
    thread_id = f"{conversation_id}:{user_id}"

    async def event_generator():
        user_context.set(user_info)

        if not is_mention:
            user_message_event = {
                "chatId": conversation_id,
                "senderId": user_id,
                "content": raw_query,
                "userId": user_id
            }
            await send_event(settings.ai_message_save_topic, user_message_event, KafkaEventType.AI_MESSAGE_SAVE)

        chat_history_context = ""
        if is_mention:
            recent_messages = await get_recent_messages(conversation_id, limit=20)
            if recent_messages:
                history_lines = []
                for m in reversed(recent_messages):
                    sender = m.get("senderName", "Unknown")
                    content = m.get("content", "")
                    time_str = (m.get("createdAt") or "")[:16].replace("T", " ")
                    history_lines.append(f"[{time_str}] {sender}: {content}")
                chat_history_context = "\n".join(history_lines)

        graph = await get_compiled_graph()
        config = {"configurable": {"thread_id": thread_id}}

        effective_query = clean_query
        if is_mention and chat_history_context:
            effective_query = (
                f"[Lịch sử hội thoại gần đây trong nhóm]:\n{chat_history_context}\n\n"
                f"[Câu hỏi của tôi (đã @mention bạn)]: {clean_query}"
            )

        input_state = {
            "conversation_id": conversation_id,
            "user_id": user_id,
            "original_query": clean_query,
            "user_query": effective_query,
            "messages": [HumanMessage(content=clean_query)]
        }

        full_response_accum = []

        async for event in graph.astream_events(input_state, config=config, version="v2"):
            kind = event["event"]
            node_name = event.get("metadata", {}).get("langgraph_node")

            if (kind == "on_chain_start" or kind == "on_chat_model_start") and node_name:
                status_map = {
                    edges.NODE_REWRITE: "REWRITING_QUERY",
                    edges.NODE_ANALYZE: "ANALYZING_INTENT",
                    edges.NODE_RETRIEVE: "RETRIEVING_VECTOR",
                    edges.NODE_GRADE: "GRADING_DATA",
                    edges.NODE_WEB_SEARCH: "WEB_SEARCHING"
                }
                if node_name in status_map:
                    status_value = status_map[node_name]
                    yield f"data: {json.dumps({'type': 'STATUS', 'content': status_value})}\n\n"

            if kind == "on_chat_model_stream":
                chunk = event["data"].get("chunk")
                if chunk and hasattr(chunk, "content") and chunk.content:
                    full_response_accum.append(chunk.content)
                    yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': chunk.content})}\n\n"

            if kind == "on_chain_end" and node_name in [edges.NODE_GENERATE, edges.NODE_CLARIFY]:
                output = event["data"].get("output", {})
                if isinstance(output, dict) and "answer" in output:
                    answer = output["answer"]
                    if answer and not "".join(full_response_accum):
                        full_response_accum.append(answer)
                        yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': answer})}\n\n"

        full_response = "".join(full_response_accum)
        
        # Fallback từ state nếu loop không bắt được content (đặc biệt cho tool-use nodes)
        if not full_response:
            final_state = await graph.aget_state(config)
            full_response = final_state.values.get("answer", "")
        
        # Gửi Kafka qua BackgroundTask để đảm bảo an toàn (kể cả khi client ngắt kết nối SSE)
        if full_response:
            background_tasks.add_task(persist_ai_response, conversation_id, full_response, user_id, is_mention)

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "X-Accel-Buffering": "no",
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )
