from typing import Literal

from pydantic import BaseModel, Field


class ParseRequest(BaseModel):
    docId: str
    conversationId: str
    s3Key: str
    fileName: str


class ChunkPreviewRequest(BaseModel):
    docId: str
    conversationId: str
    rawContent: str = ""
    strategy: Literal["fixed", "recursive", "semantic", "excel_row"] = "recursive"
    s3Key: str | None = None
    fileName: str | None = None
    chunkSize: int = Field(default=512, ge=100, le=4096)
    overlap: int = Field(default=10, ge=0, le=100)


class IngestChunkPayload(BaseModel):
    id: str
    docId: str
    vectorId: str
    prevId: str | None = None
    nextId: str | None = None
    chunkContent: str
    chunkIndex: int = Field(default=0, ge=0)
    pageNumber: int | None = Field(default=None, ge=1)
    tokenCount: int = Field(default=0, ge=0)


class IngestRequest(BaseModel):
    docId: str
    conversationId: str
    chunks: list[IngestChunkPayload]
