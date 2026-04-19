import json
import logging
from fastapi import BackgroundTasks
from langchain_core.messages import HumanMessage
from app.service.graph_manager import get_compiled_graph
from app.graph import edges
from app.security.security_context import user_context
from app.messaging.kafka_producer import send_event
from app.messaging.event_types import KafkaEventType
from app.config.app_config import settings
from app.config.constants import BONDHUB_AI_ID
from app.dto.request.chat_request import ChatRequest
from app.utils.string_utils import sanitize_ai_query
from app.client.message_client import get_recent_messages

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

class ChatService:
    async def get_chat_generator(self, chat_req: ChatRequest, user_info: dict, background_tasks: BackgroundTasks):
        raw_query = chat_req.query or chat_req.content
        clean_query = sanitize_ai_query(raw_query)
        conversation_id = chat_req.conversationId
        is_mention = chat_req.isMention
        user_id = user_info.get("user_id")
        thread_id = f"{conversation_id}:{user_id}"

        async def event_generator():
            # Đảm bảo context được truyền vào generator của thread mới
            user_context.set(user_info)

            # 1. Lưu tin nhắn người dùng nếu không phải mention (mention đã được Java lưu)
            if not is_mention:
                user_message_event = {
                    "chatId": conversation_id,
                    "senderId": user_id,
                    "content": raw_query,
                    "userId": user_id
                }
                await send_event(settings.ai_message_save_topic, user_message_event, KafkaEventType.AI_MESSAGE_SAVE)

            # 2. Chuẩn bị lịch sử hội thoại (History Context) cho mention mode
            chat_history_context = await self._prepare_history_context(conversation_id, is_mention)

            # 3. Khởi tạo Graph và Config
            graph = await get_compiled_graph()
            config = {"configurable": {"thread_id": thread_id}}

            input_state = {
                "conversation_id": conversation_id,
                "user_id": user_id,
                "original_query": clean_query,
                "user_query": clean_query,
                "chat_history_context": chat_history_context,
                "messages": [HumanMessage(content=clean_query)],
                "retry_count": 0,  # Reset retry count for new request
                "context": "",      # Clear previous context
                "grade": None,     # Reset previous grade
                "rewritten_query": None # Reset previous rewrite
            }

            full_response_accum = []

            # 4. Stream events từ Graph
            async for event in graph.astream_events(input_state, config=config, version="v2"):
                kind = event["event"]
                node_name = event.get("metadata", {}).get("langgraph_node")

                # Gửi trạng thái xử lý (STATUS)
                if (kind == "on_chain_start" or kind == "on_chat_model_start") and node_name:
                    status_event = self._handle_status_event(node_name)
                    if status_event:
                        yield f"data: {json.dumps(status_event)}\n\n"

                # Gửi từng phần câu trả lời (ANSWER_CHUNK)
                if kind == "on_chat_model_stream":
                    chunk = event["data"].get("chunk")
                    if chunk and hasattr(chunk, "content") and chunk.content:
                        full_response_accum.append(chunk.content)
                        yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': chunk.content})}\n\n"

                # Xử lý các node kết thúc không stream (như clarify)
                if kind == "on_chain_end" and node_name in [edges.NODE_GENERATE, edges.NODE_CLARIFY]:
                    output = event["data"].get("output", {})
                    if isinstance(output, dict) and "answer" in output:
                        answer = output["answer"]
                        if answer and not "".join(full_response_accum):
                            full_response_accum.append(answer)
                            yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': answer})}\n\n"

            # 5. Xử lý phản hồi cuối cùng và Persistence
            full_response = "".join(full_response_accum)
            if not full_response:
                final_state = await graph.aget_state(config)
                full_response = final_state.values.get("answer", "")

            if full_response:
                background_tasks.add_task(persist_ai_response, conversation_id, full_response, user_id, is_mention)

        return event_generator()

    async def _prepare_history_context(self, conversation_id: str, is_mention: bool) -> str:
        if not is_mention:
            return ""
            
        recent_messages = await get_recent_messages(conversation_id, limit=20)
        if not recent_messages:
            return ""

        history_lines = []
        for m in reversed(recent_messages):
            sender = m.get("senderName", "Unknown")
            content = m.get("content", "")
            time_str = (m.get("createdAt") or "")[:16].replace("T", " ")
            history_lines.append(f"[{time_str}] {sender}: {content}")
        
        return "\n".join(history_lines)

    def _handle_status_event(self, node_name: str):
        status_map = {
            edges.NODE_REWRITE: "REWRITING_QUERY",
            edges.NODE_ANALYZE: "ANALYZING_INTENT",
            edges.NODE_RETRIEVE: "RETRIEVING_VECTOR",
            edges.NODE_GRADE: "GRADING_DATA",
            edges.NODE_WEB_SEARCH: "WEB_SEARCHING",
            edges.NODE_SUMMARIZE: "SUMMARIZING_CONVERSATION"
        }
        if node_name in status_map:
            return {"type": "STATUS", "content": status_map[node_name]}
        return None

# Singleton-like provider for FastAPI Depends
_chat_service = ChatService()

def get_chat_service():
    return _chat_service
