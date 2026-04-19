from qdrant_client import AsyncQdrantClient
from qdrant_client.http import models
from beanie.odm.operators.find.comparison import In
from app.config.app_config import settings
from app.config.constants import GLOBAL_CONVERSATION_ID
from app.model.document_entity import ChunkEntity
from openai import AsyncOpenAI
import logging
from uuid import NAMESPACE_DNS, UUID, uuid5

logger = logging.getLogger(__name__)

qdrant_client = AsyncQdrantClient(
    url=settings.qdrant_url,
    api_key=settings.qdrant_api_key if settings.qdrant_api_key else None,
)

openai_client = AsyncOpenAI(api_key=settings.openai_api_key)


def _to_qdrant_point_id(chunk: dict) -> str:
    raw_id = str(chunk.get("id") or "").strip()
    if raw_id:
        try:
            return str(UUID(raw_id))
        except ValueError:
            pass

    fallback_seed = str(chunk.get("vectorId") or "")
    if fallback_seed:
        return str(uuid5(NAMESPACE_DNS, fallback_seed))

    return str(uuid5(NAMESPACE_DNS, str(chunk)))

async def check_qdrant_connection():
    try:
        await qdrant_client.get_collections()
        logger.info(f"Successfully connected to Qdrant at {settings.qdrant_url}")
    except Exception as e:
        logger.error(f"Failed to connect to Qdrant: {e}")

async def ensure_ingest_collection(vector_size: int = 1536):
    collections = await qdrant_client.get_collections()
    names = {c.name for c in collections.collections}

    if settings.qdrant_ingest_collection not in names:
        await qdrant_client.create_collection(
            collection_name=settings.qdrant_ingest_collection,
            vectors_config=models.VectorParams(size=vector_size, distance=models.Distance.COSINE),
        )

    try:
        await qdrant_client.create_payload_index(
            collection_name=settings.qdrant_ingest_collection,
            field_name="conversation_id",
            field_schema=models.PayloadSchemaType.KEYWORD,
        )
    except Exception as e:
        # Index creation is idempotent in intent; ignore "already exists" style failures.
        logger.debug("Payload index creation skipped: %s", str(e))


async def upsert_document_vectors(
    doc_id: str,
    conversation_id: str,
    chunks: list[dict],
    vectors: list[list[float]],
    ensure_collection: bool = True,
):
    if ensure_collection:
        await ensure_ingest_collection()

    safe_conversation_id = conversation_id or GLOBAL_CONVERSATION_ID
    points: list[models.PointStruct] = []
    for i, chunk in enumerate(chunks):
        payload = {
            "doc_id": doc_id,
            "conversation_id": safe_conversation_id,
        }

        if settings.enable_citation_payload:
            payload["page_number"] = chunk.get("pageNumber")
            payload["file_name"] = chunk.get("fileName")

        points.append(
            models.PointStruct(
                id=_to_qdrant_point_id(chunk),
                vector=vectors[i],
                payload=payload,
            )
        )

    if points:
        await qdrant_client.upsert(
            collection_name=settings.qdrant_ingest_collection,
            points=points,
        )


async def delete_by_doc_id(doc_id: str):
    await qdrant_client.delete(
        collection_name=settings.qdrant_ingest_collection,
        points_selector=models.FilterSelector(
            filter=models.Filter(
                must=[
                    models.FieldCondition(
                        key="doc_id",
                        match=models.MatchValue(value=doc_id),
                    )
                ]
            )
        ),
    )


async def search_similar_point_ids(query_text: str, conversation_id: str, top_k: int = 5) -> list[str]:
    try:
        await ensure_ingest_collection()

        response = await openai_client.embeddings.create(
            input=[query_text],
            model=settings.embedding_model,
        )
        query_vector = response.data[0].embedding

        safe_conversation_id = conversation_id or GLOBAL_CONVERSATION_ID
        should_conditions = [
            models.FieldCondition(
                key="conversation_id",
                match=models.MatchValue(value=safe_conversation_id),
            )
        ]
        if safe_conversation_id != GLOBAL_CONVERSATION_ID:
            should_conditions.append(
                models.FieldCondition(
                    key="conversation_id",
                    match=models.MatchValue(value=GLOBAL_CONVERSATION_ID),
                )
            )

        # Backward compatibility for old payloads keyed by chat_id.
        should_conditions.append(
            models.FieldCondition(
                key="chat_id",
                match=models.MatchValue(value=safe_conversation_id),
            )
        )

        result = await qdrant_client.query_points(
            collection_name=settings.qdrant_ingest_collection,
            query=query_vector,
            query_filter=models.Filter(should=should_conditions),
            limit=top_k,
            score_threshold=settings.qdrant_score_threshold,
        )

        if not result.points:
            return []

        return [str(hit.id) for hit in result.points]

    except Exception as e:
        logger.error(f"Qdrant query_points Error: {e}")
        return []


async def get_chunk_contents_by_point_ids(point_ids: list[str]) -> list[str]:
    if not point_ids:
        return []

    chunks = await ChunkEntity.find(In(ChunkEntity.point_id, point_ids)).to_list()
    if not chunks:
        # Backward compatibility for historical data where Qdrant point id was vector_id.
        chunks = await ChunkEntity.find(In(ChunkEntity.vector_id, point_ids)).to_list()

    chunks_by_point_id = {c.point_id: c.chunk_content for c in chunks if c.point_id}
    chunks_by_vector_id = {c.vector_id: c.chunk_content for c in chunks}

    return [
        chunks_by_point_id.get(point_id, chunks_by_vector_id.get(point_id, ""))
        for point_id in point_ids
    ]
