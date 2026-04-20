from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class ParseResponse(BaseModel):
    docId: str
    conversationId: str
    rawContent: str


class IngestAckResponse(BaseModel):
    docId: str
    conversationId: str
    status: str
    message: str


IngestDocumentStatus = Literal["INGESTING", "COMPLETED", "FAILED"]


class IngestChunkResponse(BaseModel):
    id: str
    docId: str
    vectorId: str
    prevId: str | None = None
    nextId: str | None = None
    chunkContent: str
    chunkIndex: int = Field(default=0, ge=0)
    pageNumber: int | None = Field(default=None, ge=1)
    tokenCount: int = Field(default=0, ge=0)


class ChunkPreviewResponse(BaseModel):
    docId: str
    conversationId: str
    chunks: list[IngestChunkResponse]


class IngestDocumentResponse(BaseModel):
    id: str
    conversationId: str
    fileName: str
    sourceUrl: str
    fileType: str
    checksum: str
    status: IngestDocumentStatus
    totalChunks: int = Field(default=0, ge=0)
    uploadedChunks: int = Field(default=0, ge=0)
    currentVectorId: str | None = None
    embeddingModel: str
    ingestLogs: list[str] = Field(default_factory=list)
    errorMessage: str | None = None
    displaySize: str | None = None
    uploadedAt: datetime | None = None
    updatedAt: datetime | None = None


class GetDocumentsResponse(BaseModel):
    documents: list[IngestDocumentResponse]
