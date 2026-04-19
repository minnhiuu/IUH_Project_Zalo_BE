from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from app.service.graph_manager import get_compiled_graph
from app.graph import edges
from app.security.security_context import security_context_dependency, user_context
from app.messaging.kafka_producer import send_event, send_socket_event
from app.messaging.event_types import KafkaEventType
from app.config.app_config import settings
from app.dto.request.chat_request import ChatRequest
from app.dto.request.summary_request import SummaryRequest
from app.dto.response.summary_response import SummaryResponse
from app.service.ai_service import summarize_messages, summarize_messages_stream
from langchain_core.messages import HumanMessage
import json
import logging

router = APIRouter()
logger = logging.getLogger(__name__)

@router.post("/summarize")
async def summarize(req: SummaryRequest, user_info: dict = Depends(security_context_dependency)):
    async def event_generator():
        # Re-inject context vào async generator vì ContextVar bị reset sau khi Depends yield
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
async def chat(chat_req: ChatRequest, user_info: dict = Depends(security_context_dependency)):
    # Support both 'query' and 'content' for backward compatibility
    query = chat_req.query or chat_req.content
    conversation_id = chat_req.conversationId
    
    if not query:
        raise HTTPException(status_code=400, detail="query or content is required")

    user_id = user_info.get("user_id")
    thread_id = f"{conversation_id}:{user_id}"

    async def event_generator():
        # Set security context for this async task
        user_context.set(user_info)

        # 1. Persist User Message to Kafka
        user_message_event = {
            "chatId": conversation_id,
            "senderId": user_id,
            "content": query,
            "userId": user_id
        }
        await send_event(settings.ai_message_save_topic, user_message_event, KafkaEventType.AI_MESSAGE_SAVE)

        graph = await get_compiled_graph()
        config = {"configurable": {"thread_id": thread_id}}
        
        state_snapshot = await graph.aget_state(config)
        history_msgs = state_snapshot.values.get("messages", [])
        logger.info(f"Loaded {len(history_msgs)} messages from history for thread {thread_id}")

        input_state = {
            "conversation_id": conversation_id,
            "user_id": user_id,
            "original_query": query,
            "user_query": query,
            "messages": [HumanMessage(content=query)]
        }
        
        full_response = ""
        
        async for event in graph.astream_events(input_state, config=config, version="v2"):
            kind = event["event"]
            node_name = event.get("metadata", {}).get("langgraph_node")
            
            # DEBUG: Log tất cả các event để phân tích luồng
            logger.info(f"[STREAM EVENT] kind={kind} | node={node_name}")
            
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
                    
                    await send_socket_event(
                        target_user_id=user_id,
                        event_type="NOTIFICATION",
                        destination="/queue/ai-status",
                        payload={"conversationId": conversation_id, "status": status_value}
                    )
            
            if kind == "on_chat_model_stream":
                # Lấy chunk content từ OpenAI/LangChain event
                chunk = event["data"].get("chunk")
                if chunk and hasattr(chunk, "content"):
                    content = chunk.content
                    logger.info(f"[STREAM CHUNK] node={node_name} | len={len(content)} | preview={repr(content[:30])}")
                    if isinstance(content, str) and content:
                        full_response += content
                        yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': content})}\n\n"
                    elif isinstance(content, str): # Chunk rỗng nhưng là stream start
                         yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': ''})}\n\n"

            if kind == "on_chat_model_end" and node_name == edges.NODE_GENERATE:
                content = event["data"]["output"].content
                logger.info(f"[MODEL END] node={node_name} | full_response_so_far_len={len(full_response)}")
                if content and not full_response:
                    full_response = content
                    yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': content})}\n\n"


            if kind == "on_chain_end" and node_name in [edges.NODE_GENERATE, edges.NODE_CLARIFY]:
                output = event["data"].get("output", {})
                if isinstance(output, dict) and "answer" in output:
                    answer = output["answer"]
                    if answer and not full_response:
                        full_response = answer
                        yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': answer})}\n\n"

        if full_response:
            save_event = {
                "chatId": conversation_id,
                "senderId": "ai-assistant-001",
                "content": full_response,
                "userId": user_id
            }
            await send_event(settings.ai_message_save_topic, save_event, KafkaEventType.AI_MESSAGE_SAVE)
            logger.info(f"Persisted AI response to Kafka for chat {conversation_id}")

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "X-Accel-Buffering": "no",   # Ngăn Nginx/Gateway buffer SSE
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )
