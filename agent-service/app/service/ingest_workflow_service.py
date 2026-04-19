from fastapi import BackgroundTasks

from app.dto.request.ingest_request import ChunkPreviewRequest, IngestRequest, ParseRequest
from app.dto.response.ingest_response import (
    ChunkPreviewResponse,
    GetDocumentsResponse,
    IngestAckResponse,
    IngestChunkResponse,
    ParseResponse,
)
from app.service.ingest_progress_service import IngestProgressService
from app.service.ingest_service import IngestService
from app.i18n import get_request_locale, translate


class IngestWorkflowService:
    def __init__(
        self,
        ingest_service: IngestService | None = None,
        progress_service: IngestProgressService | None = None,
    ):
        self.ingest_service = ingest_service or IngestService()
        self.progress_service = progress_service or IngestProgressService()

    async def parse_document(self, req: ParseRequest) -> ParseResponse:
        raw_content = await self.ingest_service.parse_step(req.docId, req.conversationId, req.s3Key, req.fileName)
        return ParseResponse(docId=req.docId, conversationId=req.conversationId, rawContent=raw_content)

    async def chunk_preview(self, req: ChunkPreviewRequest) -> ChunkPreviewResponse:
        chunks = await self.ingest_service.chunk_preview_step(
            req.docId,
            req.conversationId,
            req.rawContent,
            req.strategy,
            req.chunkSize,
            req.overlap,
            req.s3Key,
            req.fileName,
        )
        return ChunkPreviewResponse(
            docId=req.docId,
            conversationId=req.conversationId,
            chunks=[IngestChunkResponse.model_validate(chunk) for chunk in chunks],
        )

    async def schedule_ingest(self, req: IngestRequest, background_tasks: BackgroundTasks) -> IngestAckResponse:
        started = await self.progress_service.try_mark_ingest_started(
            req.docId,
            req.conversationId,
            len(req.chunks),
            get_request_locale(),
        )
        if not started:
            return IngestAckResponse(
                docId=req.docId,
                conversationId=req.conversationId,
                status="INGESTING",
                message=translate("ingest.already.running"),
            )

        background_tasks.add_task(
            self._run_ingest_and_release,
            req.docId,
            req.conversationId,
            [chunk.model_dump() for chunk in req.chunks],
        )

        return IngestAckResponse(
            docId=req.docId,
            conversationId=req.conversationId,
            status="INGESTING",
            message=translate("ingest.started"),
        )

    async def get_documents(self, conversation_id: str | None = None) -> GetDocumentsResponse:
        documents = await self.ingest_service.get_documents(conversation_id)
        enriched_documents = await self.progress_service.enrich_documents(documents)
        return GetDocumentsResponse(documents=enriched_documents)

    async def _run_ingest_and_release(self, doc_id: str, conversation_id: str, chunks: list[dict]) -> None:
        try:
            await self.ingest_service.ingest_step(
                doc_id,
                conversation_id,
                chunks,
                progress_callback=lambda event: self.progress_service.handle_progress_event(doc_id, event),
            )
        except Exception as exc:
            await self.progress_service.mark_ingest_failed(doc_id, len(chunks), str(exc))
        finally:
            await self.progress_service.release_ingest(doc_id)
