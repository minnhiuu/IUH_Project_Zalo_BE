from qdrant_client import AsyncQdrantClient
from qdrant_client.http import models
from app.core.config import settings
from openai import AsyncOpenAI
import logging

logger = logging.getLogger(__name__)

qdrant_client = AsyncQdrantClient(
    url=settings.qdrant_url,
    api_key=settings.qdrant_api_key if settings.qdrant_api_key else None,
)

openai_client = AsyncOpenAI(api_key=settings.openai_api_key)

async def check_qdrant_connection():
    try:
        await qdrant_client.get_collections()
        logger.info(f"Successfully connected to Qdrant at {settings.qdrant_url}")
    except Exception as e:
        logger.error(f"Failed to connect to Qdrant: {e}")

# app/integration/qdrant.py

async def search_similar(query_text: str, conversation_id: str, top_k: int = 5) -> str:
    try:
        # 1. Tạo embedding (giữ nguyên logic cũ của Huy)
        response = await openai_client.embeddings.create(
            input=[query_text],
            model="text-embedding-3-small"
        )
        query_vector = response.data[0].embedding

        # 2. Sử dụng query_points (API mới nhất của Qdrant 1.9+)
        # query_points trả về một QueryResponse object
        response = await qdrant_client.query_points(
            collection_name=settings.qdrant_collection_name,
            query=query_vector,  # Lưu ý: query_points dùng 'query' thay cho 'query_vector'
            query_filter=models.Filter(
                must=[
                    models.FieldCondition(
                        key="chat_id",
                        match=models.MatchValue(value=conversation_id),
                    )
                ]
            ),
            limit=top_k,
            score_threshold=0.7
        )

        # 3. Trích xuất dữ liệu từ kết quả
        # query_points trả về danh sách ScoredPoint tương tự search
        if not response.points:
            return ""

        contexts = [
            hit.payload.get("text", "") 
            for hit in response.points 
            if hit.payload
        ]
        
        return "\n---\n".join(contexts)

    except Exception as e:
        logger.error(f"Qdrant query_points Error: {e}")
        return ""
