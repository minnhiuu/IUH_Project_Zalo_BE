from asyncio import Lock
from datetime import datetime
from typing import Any

from app.dto.response.ingest_response import IngestDocumentResponse
from app.i18n import translate


class IngestProgressService:
    def __init__(self, max_log_lines: int = 200):
        self._active_ingest_doc_ids: set[str] = set()
        self._active_ingest_lock = Lock()
        self._ingest_progress: dict[str, dict[str, Any]] = {}
        self._ingest_progress_lock = Lock()
        self._max_log_lines = max_log_lines

    async def try_mark_ingest_started(
        self,
        doc_id: str,
        conversation_id: str,
        total_chunks: int,
        locale: str = "vi",
    ) -> bool:
        async with self._active_ingest_lock:
            if doc_id in self._active_ingest_doc_ids:
                return False
            self._active_ingest_doc_ids.add(doc_id)

        async with self._ingest_progress_lock:
            self._ingest_progress[doc_id] = {
                "doc_id": doc_id,
                "conversation_id": conversation_id,
                "locale": locale,
                "status": "PROCESSING",
                "uploaded_chunks": 0,
                "total_chunks": total_chunks,
                "current_vector_id": None,
                "error_message": None,
                "ingest_logs": [
                    self._format_log_line(
                        translate("ingest.progress.uploaded_ratio", locale=locale, uploaded=0, total=total_chunks)
                    ),
                ],
                "started_at": datetime.utcnow().isoformat(),
                "updated_at": datetime.utcnow().isoformat(),
            }

        return True

    async def release_ingest(self, doc_id: str) -> None:
        async with self._active_ingest_lock:
            self._active_ingest_doc_ids.discard(doc_id)

    async def handle_progress_event(self, doc_id: str, event: dict[str, Any]) -> None:
        locale = await self._get_progress_locale(doc_id)
        event_type = event.get("type")
        total_chunks = int(event.get("total_chunks") or 0)
        uploaded_chunks = int(event.get("uploaded_chunks") or 0)
        vector_id = event.get("vector_id")

        if event_type == "chunk_started":
            await self._update_ingest_progress(
                doc_id,
                status="PROCESSING",
                total_chunks=total_chunks,
                uploaded_chunks=uploaded_chunks,
                current_vector_id=vector_id,
                message=translate(
                    "ingest.progress.chunk_uploading",
                    locale=locale,
                    vectorId=str(vector_id or ""),
                ),
            )
            return

        if event_type == "chunk_completed":
            await self._update_ingest_progress(
                doc_id,
                status="PROCESSING",
                total_chunks=total_chunks,
                uploaded_chunks=uploaded_chunks,
                current_vector_id="",
                message=translate(
                    "ingest.progress.chunk_uploaded",
                    locale=locale,
                    vectorId=str(vector_id or ""),
                ),
            )
            await self._update_ingest_progress(
                doc_id,
                message=translate(
                    "ingest.progress.uploaded_ratio",
                    locale=locale,
                    uploaded=uploaded_chunks,
                    total=total_chunks,
                ),
            )
            return

        if event_type == "completed":
            await self._update_ingest_progress(
                doc_id,
                status="COMPLETED",
                uploaded_chunks=uploaded_chunks,
                total_chunks=total_chunks,
                current_vector_id="",
                message=translate("ingest.progress.completed", locale=locale),
            )
            return

        if event_type == "failed":
            error = str(event.get("error_message") or translate("error.sys.uncategorized", locale=locale))
            await self._update_ingest_progress(
                doc_id,
                status="FAILED",
                uploaded_chunks=uploaded_chunks,
                total_chunks=total_chunks,
                current_vector_id="",
                error_message=error,
                message=translate("ingest.progress.failed", locale=locale, reason=error),
            )

    async def mark_ingest_failed(self, doc_id: str, total_chunks: int, error_message: str) -> None:
        await self.handle_progress_event(
            doc_id,
            {
                "type": "failed",
                "uploaded_chunks": 0,
                "total_chunks": total_chunks,
                "error_message": error_message,
            },
        )

    async def enrich_documents(self, documents: list[Any]) -> list[IngestDocumentResponse]:
        async with self._ingest_progress_lock:
            progress_snapshot: dict[str, dict[str, Any]] = {}
            for key, value in self._ingest_progress.items():
                progress_snapshot[key] = {
                    **value,
                    "ingest_logs": list(value.get("ingest_logs", [])),
                }

        enriched_documents: list[IngestDocumentResponse] = []
        for doc in documents:
            progress = progress_snapshot.get(doc.doc_id)
            backend_status = doc.status
            base_total = int(doc.total_chunks or 0)
            base_uploaded = base_total if backend_status == "COMPLETED" else 0

            total_chunks = base_total
            uploaded_chunks = base_uploaded
            current_vector_id = None
            error_message = doc.error_message
            ingest_logs: list[str] = []

            if progress:
                total_chunks = int(progress.get("total_chunks") or total_chunks)
                uploaded_chunks = int(progress.get("uploaded_chunks") or uploaded_chunks)
                current_vector_id = progress.get("current_vector_id")
                error_message = progress.get("error_message") or error_message
                ingest_logs = progress.get("ingest_logs", [])

            if backend_status == "COMPLETED":
                total_chunks = max(total_chunks, base_total)
                uploaded_chunks = max(uploaded_chunks, total_chunks)

            enriched_documents.append(
                IngestDocumentResponse(
                    id=doc.doc_id,
                    conversationId=doc.conversation_id,
                    fileName=doc.file_name,
                    sourceUrl=doc.s3_key,
                    fileType=doc.file_type,
                    checksum=doc.checksum,
                    status=self._to_frontend_status(backend_status),
                    totalChunks=total_chunks,
                    uploadedChunks=uploaded_chunks,
                    currentVectorId=current_vector_id,
                    embeddingModel=doc.embedding_model,
                    errorMessage=error_message,
                    ingestLogs=ingest_logs,
                    uploadedAt=doc.uploaded_at,
                    updatedAt=doc.updated_at,
                )
            )

        return enriched_documents

    def _format_log_line(self, message: str) -> str:
        stamp = datetime.now().strftime("%H:%M:%S")
        return f"[{stamp}] {message}"

    def _to_frontend_status(self, status: str) -> str:
        if status == "COMPLETED":
            return "COMPLETED"
        if status == "FAILED":
            return "FAILED"
        return "INGESTING"

    async def _update_ingest_progress(
        self,
        doc_id: str,
        *,
        status: str | None = None,
        uploaded_chunks: int | None = None,
        total_chunks: int | None = None,
        current_vector_id: str | None = None,
        error_message: str | None = None,
        message: str | None = None,
    ) -> None:
        async with self._ingest_progress_lock:
            progress = self._ingest_progress.get(doc_id)
            if progress is None:
                return

            if status is not None:
                progress["status"] = status
            if uploaded_chunks is not None:
                progress["uploaded_chunks"] = uploaded_chunks
            if total_chunks is not None:
                progress["total_chunks"] = total_chunks
            if current_vector_id is not None or current_vector_id == "":
                progress["current_vector_id"] = current_vector_id or None
            if error_message is not None:
                progress["error_message"] = error_message

            if message:
                progress_logs = progress.setdefault("ingest_logs", [])
                progress_logs.append(self._format_log_line(message))
                if len(progress_logs) > self._max_log_lines:
                    progress["ingest_logs"] = progress_logs[-self._max_log_lines:]

            progress["updated_at"] = datetime.utcnow().isoformat()

    async def _get_progress_locale(self, doc_id: str) -> str:
        async with self._ingest_progress_lock:
            progress = self._ingest_progress.get(doc_id)
            if progress is None:
                return "vi"
            return str(progress.get("locale") or "vi")
