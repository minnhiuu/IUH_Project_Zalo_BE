from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query

from app.dto.request.ingest_request import ChunkPreviewRequest, IngestRequest, ParseRequest
from app.dto.response.ingest_response import ChunkPreviewResponse, GetDocumentsResponse, IngestAckResponse, ParseResponse
from app.security.security_context import security_context_dependency
from app.service.ingest_workflow_service import IngestWorkflowService

router = APIRouter()
workflow_service = IngestWorkflowService()


@router.post("/ingest/parse", response_model=ParseResponse)
async def parse_document(req: ParseRequest, user_info: dict = Depends(security_context_dependency)):
    return await workflow_service.parse_document(req)


@router.post("/ingest/chunk-preview", response_model=ChunkPreviewResponse)
async def chunk_preview(req: ChunkPreviewRequest, user_info: dict = Depends(security_context_dependency)):
    return await workflow_service.chunk_preview(req)


@router.post("/ingest", response_model=IngestAckResponse)
async def ingest_document(
    req: IngestRequest,
    background_tasks: BackgroundTasks,
    user_info: dict = Depends(security_context_dependency),
):
    if not req.chunks:
        raise HTTPException(status_code=400, detail="chunks must not be empty")

    return await workflow_service.schedule_ingest(req, background_tasks)


@router.get("/ingest/documents", response_model=GetDocumentsResponse)
async def get_documents(
    conversationId: str | None = Query(default=None, alias="conversationId"),
    user_info: dict = Depends(security_context_dependency),
):
    return await workflow_service.get_documents(conversationId)
