from beanie import init_beanie
from motor.motor_asyncio import AsyncIOMotorClient

from app.config.app_config import settings
from app.model.document_entity import ChunkEntity, DocumentEntity

_mongo_client: AsyncIOMotorClient | None = None


async def init_mongodb() -> None:
    global _mongo_client
    if _mongo_client is not None:
        return

    _mongo_client = AsyncIOMotorClient(settings.mongodb_uri)
    default_db = _mongo_client.get_default_database()
    if default_db is None:
        raise RuntimeError("mongodb_uri must include a database name")

    await init_beanie(
        database=default_db,
        document_models=[DocumentEntity, ChunkEntity],
    )


async def close_mongodb() -> None:
    global _mongo_client
    if _mongo_client is not None:
        _mongo_client.close()
        _mongo_client = None
