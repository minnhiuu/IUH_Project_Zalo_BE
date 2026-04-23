import hashlib
import logging
import os
import tempfile
from datetime import datetime
from pathlib import Path
from typing import Any, Awaitable, Callable
from uuid import uuid4

import httpx
import pandas as pd
from llama_index.core import Document as LlamaDocument
from llama_index.core.node_parser import SemanticSplitterNodeParser, SentenceSplitter
from llama_index.embeddings.openai import OpenAIEmbedding
from llama_index.readers.file import DocxReader, PDFReader

from app.client.qdrant_client import delete_by_doc_id, ensure_ingest_collection, upsert_document_vectors
from app.config.app_config import settings
from app.config.constants import GLOBAL_CONVERSATION_ID
from app.exception import AppException, ErrorCode
from app.model.document_entity import ChunkEntity, DocumentEntity
from app.utils.s3_utils import S3Utils

logger = logging.getLogger(__name__)


class IngestService:
    def __init__(self):
        self.embedder = OpenAIEmbedding(model=settings.embedding_model)
        self.s3 = S3Utils()

    async def parse_step(self, doc_id: str, conversation_id: str, s3_key: str, file_name: str) -> str:
        safe_conversation_id = conversation_id or GLOBAL_CONVERSATION_ID
        suffix = Path(file_name).suffix.lower()
        presigned_url = self.s3.get_presigned_url(s3_key)
        tmp_path, checksum = await self._download_to_tempfile(presigned_url, suffix)

        try:
            raw_text = self._read_text_from_file(tmp_path, suffix)
        finally:
            os.remove(tmp_path)

        existing = await DocumentEntity.find_one(DocumentEntity.doc_id == doc_id)
        payload = {
            "conversation_id": safe_conversation_id,
            "file_name": file_name,
            "file_type": suffix.replace(".", ""),
            "s3_key": s3_key,
            "checksum": checksum,
            "status": "PROCESSING",
            "error_message": None,
            "embedding_model": settings.embedding_model,
            "updated_at": datetime.utcnow(),
        }

        if existing is None:
            await DocumentEntity(
                doc_id=doc_id,
                uploaded_at=datetime.utcnow(),
                total_chunks=0,
                **payload,
            ).insert()
        else:
            await existing.set(payload)

        return raw_text

    async def chunk_preview_step(
        self,
        doc_id: str,
        conversation_id: str,
        raw_content: str,
        strategy: str,
        chunk_size: int,
        overlap: int,
        s3_key: str | None = None,
        file_name: str | None = None,
    ):
        safe_conversation_id = conversation_id or GLOBAL_CONVERSATION_ID

        if strategy == "excel_row":
            if not settings.excel_row_chunking_enabled:
                raise AppException(ErrorCode.INGEST_EXCEL_STRATEGY_DISABLED)
            if not s3_key or not file_name:
                raise AppException(ErrorCode.INGEST_EXCEL_PARAMS_REQUIRED)

            suffix = Path(file_name).suffix.lower()
            if suffix not in {".xlsx", ".xls"}:
                raise AppException(ErrorCode.INGEST_EXCEL_FILETYPE_INVALID)

            presigned_url = self.s3.get_presigned_url(s3_key)
            tmp_path, _ = await self._download_to_tempfile(presigned_url, suffix)
            try:
                return self._parse_excel_by_row(tmp_path, doc_id, safe_conversation_id)
            finally:
                os.remove(tmp_path)

        parser = (
            SemanticSplitterNodeParser(embed_model=self.embedder)
            if strategy == "semantic"
            else SentenceSplitter(chunk_size=chunk_size, chunk_overlap=overlap)
        )

        nodes = parser.get_nodes_from_documents([LlamaDocument(text=raw_content)])
        chunks: list[dict] = []

        for idx, node in enumerate(nodes):
            chunks.append(
                {
                    "id": str(uuid4()),
                    "docId": doc_id,
                    "conversationId": safe_conversation_id,
                    "vectorId": f"{doc_id}_{idx}",
                    "chunkContent": node.text,
                    "chunkIndex": idx,
                    "pageNumber": node.metadata.get("page_label") if node.metadata else None,
                    "tokenCount": len(node.text.split()),
                }
            )

        return chunks

    async def ingest_step(
        self,
        doc_id: str,
        conversation_id: str,
        chunks: list[dict],
        progress_callback: Callable[[dict[str, Any]], Awaitable[None]] | None = None,
    ):
        safe_conversation_id = conversation_id or GLOBAL_CONVERSATION_ID

        try:
            await DocumentEntity.find_one(DocumentEntity.doc_id == doc_id).update(
                {
                    "$set": {
                        "status": "PROCESSING",
                        "total_chunks": len(chunks),
                        "error_message": None,
                        "updated_at": datetime.utcnow(),
                    }
                }
            )

            # Re-ingest should replace old chunks and vectors for this document.
            await ChunkEntity.find(ChunkEntity.doc_id == doc_id).delete()
            await delete_by_doc_id(doc_id)
            await ensure_ingest_collection()

            for idx, chunk in enumerate(chunks, start=1):
                uploaded_before = idx - 1
                if progress_callback is not None:
                    await progress_callback(
                        {
                            "type": "chunk_started",
                            "vector_id": chunk.get("vectorId"),
                            "uploaded_chunks": uploaded_before,
                            "total_chunks": len(chunks),
                        }
                    )

                embedding = await self.embedder.aget_text_embedding(chunk["chunkContent"])

                await upsert_document_vectors(
                    doc_id=doc_id,
                    conversation_id=safe_conversation_id,
                    chunks=[chunk],
                    vectors=[embedding],
                    ensure_collection=False,
                )

                await ChunkEntity(
                    doc_id=doc_id,
                    conversation_id=safe_conversation_id,
                    point_id=chunk.get("id"),
                    vector_id=chunk["vectorId"],
                    chunk_content=chunk["chunkContent"],
                    chunk_index=chunk["chunkIndex"],
                    page_number=chunk.get("pageNumber"),
                    token_count=chunk.get("tokenCount", 0),
                    embedding_model=settings.embedding_model,
                ).insert()

                if progress_callback is not None:
                    await progress_callback(
                        {
                            "type": "chunk_completed",
                            "vector_id": chunk.get("vectorId"),
                            "uploaded_chunks": idx,
                            "total_chunks": len(chunks),
                        }
                    )

            await DocumentEntity.find_one(DocumentEntity.doc_id == doc_id).update(
                {
                    "$set": {
                        "status": "COMPLETED",
                        "total_chunks": len(chunks),
                        "embedding_model": settings.embedding_model,
                        "updated_at": datetime.utcnow(),
                    }
                }
            )

            if progress_callback is not None:
                await progress_callback(
                    {
                        "type": "completed",
                        "uploaded_chunks": len(chunks),
                        "total_chunks": len(chunks),
                    }
                )

        except Exception as e:
            logger.error("Ingest failed for doc %s: %s", doc_id, str(e))

            # Rollback partial ingest state to keep document consistency.
            await ChunkEntity.find(ChunkEntity.doc_id == doc_id).delete()
            await delete_by_doc_id(doc_id)

            await DocumentEntity.find_one(DocumentEntity.doc_id == doc_id).update(
                {
                    "$set": {
                        "status": "FAILED",
                        "error_message": str(e),
                        "updated_at": datetime.utcnow(),
                    }
                }
            )

            if progress_callback is not None:
                await progress_callback(
                    {
                        "type": "failed",
                        "error_message": str(e),
                        "uploaded_chunks": 0,
                        "total_chunks": len(chunks),
                    }
                )
            raise

    async def get_documents(self, conversation_id: str | None = None):
        safe_conversation_id = conversation_id or GLOBAL_CONVERSATION_ID
        if conversation_id:
            return await DocumentEntity.find(DocumentEntity.conversation_id == safe_conversation_id).to_list()
        return await DocumentEntity.find_all().to_list()

    async def _download_to_tempfile(self, presigned_url: str, suffix: str) -> tuple[str, str]:
        hasher = hashlib.sha256()

        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp_path = tmp.name
            async with httpx.AsyncClient(timeout=120) as client:
                async with client.stream("GET", presigned_url) as resp:
                    resp.raise_for_status()
                    async for chunk in resp.aiter_bytes(chunk_size=1024 * 1024):
                        hasher.update(chunk)
                        tmp.write(chunk)

        return tmp_path, f"sha256:{hasher.hexdigest()}"

    def _read_text_from_file(self, file_path: str, suffix: str) -> str:
        if suffix == ".pdf":
            docs = PDFReader().load_data(file=file_path)
            return "\n\n".join([d.text for d in docs if getattr(d, "text", "")])

        if suffix == ".docx":
            docs = DocxReader().load_data(file=file_path)
            return "\n\n".join([d.text for d in docs if getattr(d, "text", "")])

        if suffix in {".xlsx", ".xls"}:
            rows = self._parse_excel_by_row(file_path, doc_id="preview", conversation_id="preview")
            return "\n".join([r["chunkContent"] for r in rows])

        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            return f.read()

    def _parse_excel_by_row(self, file_path: str, doc_id: str, conversation_id: str):
        df = pd.read_excel(file_path)
        df = df.dropna(how="all")
        headers = df.columns.tolist()
        chunks: list[dict] = []

        for row_index, row in df.reset_index(drop=True).iterrows():
            pairs: list[str] = []
            for i, col in enumerate(headers):
                value = row.iloc[i]
                if pd.isna(value):
                    continue
                pairs.append(f"{col}: {value}")

            row_content = " | ".join(pairs)
            if not row_content:
                continue

            chunks.append(
                {
                    "id": str(uuid4()),
                    "docId": doc_id,
                    "conversationId": conversation_id,
                    "vectorId": f"{doc_id}_row_{row_index}",
                    "chunkContent": row_content,
                    "chunkIndex": row_index,
                    "pageNumber": 1,
                    "tokenCount": len(row_content.split()),
                }
            )

        return chunks
