from fastapi import APIRouter, Request, Depends, HTTPException
from fastapi.responses import StreamingResponse
from app.graph.workflow import get_compiled_graph
from app.graph import edges
from app.core.security import security_context_dependency, get_user_context
from app.kafka.producer import send_message, send_socket_event
from langchain_core.messages import HumanMessage
import json
import logging
import datetime

router = APIRouter()
logger = logging.getLogger(__name__)

@router.post("/chat")
async def chat(request: Request, user_info: dict = Depends(security_context_dependency)):
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON body")

    # Support 'content' for compatibility with frontend
    query = body.get("query") or body.get("content")
    conversation_id = body.get("conversationId")
    if not query or not conversation_id:
        raise HTTPException(status_code=400, detail="query (content) and conversationId are required")

    user_id = user_info.get("user_id")
    # Thread ID for checkpoint consistency
    thread_id = f"{conversation_id}:{user_id}"

    async def event_generator():
        # IMPORTANT: Preserve security context inside the async generator
        from app.core.security import user_context
        from app.core.config import settings
        user_context.set(user_info)

        # 1. Persist User Message to Kafka (if it's a new query)
        user_message_event = {
            "chatId": conversation_id,
            "senderId": user_id,
            "content": query,
            "userId": user_id
        }
        await send_message(settings.ai_message_save_topic, user_message_event)

        graph = await get_compiled_graph()
        config = {"configurable": {"thread_id": thread_id}}
        
        # Check current state to log history length (Debug)
        state_snapshot = await graph.aget_state(config)
        history_msgs = state_snapshot.values.get("messages", [])
        logger.info(f"Loaded {len(history_msgs)} messages from history for thread {thread_id}")

        # Initial state to merge (LangGraph will add the new message to existing history)
        input_state = {
            "conversation_id": conversation_id,
            "user_id": user_id,
            "original_query": query,
            "user_query": query, # Set current user msg explicitly
            "messages": [HumanMessage(content=query)]
        }
        
        full_response = ""
        
        async for event in graph.astream_events(input_state, config=config, version="v2"):
            kind = event["event"]
            node_name = event.get("metadata", {}).get("langgraph_node")
            
            # Map events to STATUS updates: Check both chain start and model start
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
                    # Avoid duplicate emits if both chain and model start
                    # 1. Send via SSE
                    yield f"data: {json.dumps({'type': 'STATUS', 'content': status_value})}\n\n"
                    
                    # 2. Send via Kafka (Pusher pattern)
                    await send_socket_event(
                        target_user_id=user_id,
                        event_type="NOTIFICATION",
                        destination="/queue/ai-status",
                        payload={"conversationId": conversation_id, "status": status_value}
                    )
            
            # Handle chunk stream for real-time FE updates
            if kind == "on_chat_model_stream" and node_name == edges.NODE_GENERATE:
                chunk = event["data"]["chunk"].content
                if chunk and isinstance(chunk, str):
                    full_response += chunk
                    yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': chunk})}\n\n"

            # Fallback for models/nodes that don't support streaming or if chunks were missed
            if kind == "on_chat_model_end" and node_name == edges.NODE_GENERATE:
                content = event["data"]["output"].content
                if content and not full_response:
                    full_response = content
                    yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': content})}\n\n"

            # Capture results from 'clarify' or 'generate' node completion (state updates)
            if kind == "on_chain_end" and node_name in [edges.NODE_GENERATE, edges.NODE_CLARIFY]:
                output = event["data"].get("output", {})
                if isinstance(output, dict) and "answer" in output:
                    answer = output["answer"]
                    if answer and not full_response:
                        full_response = answer
                        yield f"data: {json.dumps({'type': 'ANSWER_CHUNK', 'content': answer})}\n\n"

        # At the end of stream, persist the message via Kafka
        if full_response:
            # Match Java's AiMessageSaveEvent: chatId, senderId, content, userId
            save_event = {
                "chatId": conversation_id,
                "senderId": "ai-assistant-001", # Match FE AI_ASSISTANT_ID
                "content": full_response,
                "userId": user_id
            }
            await send_message(settings.ai_message_save_topic, save_event)
            logger.info(f"Persisted AI response to Kafka for chat {conversation_id}")

    return StreamingResponse(event_generator(), media_type="text/event-stream")
