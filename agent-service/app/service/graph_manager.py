from langgraph.checkpoint.mongodb.aio import AsyncMongoDBSaver
from motor.motor_asyncio import AsyncIOMotorClient
from app.config.app_config import settings
from app.graph.workflow import build_graph

# Global checkpointer singleton
_checkpointer = None

async def get_checkpointer():
    global _checkpointer
    if _checkpointer is None:
        client = AsyncIOMotorClient(settings.mongodb_uri)
        db_name = client.get_default_database().name
        _checkpointer = AsyncMongoDBSaver(client, db_name=db_name)
    return _checkpointer

async def get_compiled_graph():
    checkpointer = await get_checkpointer()
    graph = build_graph()
    return graph.compile(checkpointer=checkpointer)
