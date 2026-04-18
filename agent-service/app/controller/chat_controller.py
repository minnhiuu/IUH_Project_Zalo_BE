from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from app.service.graph_manager import get_compiled_graph
from app.graph import edges
from app.security.security_context import security_context_dependency, user_context
from app.messaging.kafka_producer import send_message, send_socket_event
from app.config.app_config import settings
from app.dto.request.chat_request import ChatRequest
from langchain_core.messages import HumanMessage
import json
import logging

router = APIRouter()
logger = logging.getLogger(__name__)

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
        await send_message(settings.ai_message_save_topic, user_message_event)

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
            
            if (kind == "on_chain_start" or kind == "on_chat_model_start") and node_name:
                status_map = {
                    edges.NODE_REWRITE: "REWRITING_QUERY",
                    edges.NODE_ANALYZE: "ANALYZING_INTENT",
                    edges.NODE_RETRIEVE: "RETRIEVING_VECTOR",
                    edges.NODE_GRADE: "GRADING_DATA",
                    edges.NODE_WEB_SEARCH: "WEB_SEARCHING",
                    edges.NODE_GENERATE: "GENERATING_ANSWER"
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
            
            if kind == "on_chat_model_stream" and node_name == edges.NODE_GENERATE:
                chunk = event["data"]["chunk"].content
                if chunk and isinstance(chunk, str):
                    full_response += chunk
                    yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': chunk})}\n\n"

            if kind == "on_chat_model_end" and node_name == edges.NODE_GENERATE:
                content = event["data"]["output"].content
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
            await send_message(settings.ai_message_save_topic, save_event)
            logger.info(f"Persisted AI response to Kafka for chat {conversation_id}")

    return StreamingResponse(event_generator(), media_type="text/event-stream")
