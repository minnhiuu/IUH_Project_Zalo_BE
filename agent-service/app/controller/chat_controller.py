from fastapi import APIRouter, Depends, HTTPException, BackgroundTasks
from fastapi.responses import StreamingResponse
from app.security.security_context import security_context_dependency
from app.dto.request.chat_request import ChatRequest
from app.dto.request.summary_request import SummaryRequest
from app.dto.response.chat_response import SummaryStreamEventResponse
from app.service.chat_service import ChatService, get_chat_service
from app.service.ai_service import summarize_messages_stream
from app.security.security_context import user_context
import json
import logging

router = APIRouter()
logger = logging.getLogger(__name__)

@router.post("/summarize")
async def summarize(req: SummaryRequest, user_info: dict = Depends(security_context_dependency)):
    async def event_generator():
        user_context.set(user_info)
        async for token in summarize_messages_stream(req.conversationId, req.sinceMessageId):
            payload = SummaryStreamEventResponse(content=token)
            yield f"data: {json.dumps(payload.model_dump())}\n\n"

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
    chat_service: ChatService = Depends(get_chat_service),
    user_info: dict = Depends(security_context_dependency)
):
    # Validation
    if not (chat_req.query or chat_req.content):
        raise HTTPException(status_code=400, detail="query or content is required")

    return StreamingResponse(
        await chat_service.get_chat_generator(chat_req, user_info, background_tasks),
        media_type="text/event-stream",
        headers={
            "X-Accel-Buffering": "no",
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )
