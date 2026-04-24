from datetime import datetime

from beanie import Document, Indexed
from pydantic import Field


class DocumentEntity(Document):
    doc_id: Indexed(str, unique=True)
    conversation_id: Indexed(str)
    file_name: str
    file_type: str
    s3_key: str
    checksum: str
    status: str = "PROCESSING"
    total_chunks: int = 0
    embedding_model: str
    error_message: str | None = None
    uploaded_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "ingest_documents"


class ChunkEntity(Document):
    doc_id: Indexed(str)
    conversation_id: Indexed(str)
    point_id: Indexed(str) | None = None
    vector_id: Indexed(str, unique=True)
    chunk_content: str
    chunk_index: int
    page_number: int | None = None
    token_count: int = 0
    embedding_model: str
    created_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "ingest_chunks"
