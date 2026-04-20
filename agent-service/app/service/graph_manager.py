from langgraph.checkpoint.mongodb.aio import AsyncMongoDBSaver
from pymongo import AsyncMongoClient

from app.config.app_config import settings
from app.graph.workflow import build_graph

# Global checkpointer singleton
_checkpointer = None
_checkpointer_client = None

async def get_checkpointer():
    global _checkpointer, _checkpointer_client
    if _checkpointer is None:
        _checkpointer_client = AsyncMongoClient(settings.mongodb_uri)
        default_db = _checkpointer_client.get_default_database()
        db_name = default_db.name if default_db is not None else "langgraph_checkpoints"
        _checkpointer = AsyncMongoDBSaver(_checkpointer_client, db_name=db_name)
    return _checkpointer


async def close_checkpointer() -> None:
    global _checkpointer, _checkpointer_client
    _checkpointer = None
    if _checkpointer_client is not None:
        _checkpointer_client.close()
        _checkpointer_client = None


async def get_compiled_graph():
    checkpointer = await get_checkpointer()
    graph = build_graph()
    return graph.compile(checkpointer=checkpointer)
