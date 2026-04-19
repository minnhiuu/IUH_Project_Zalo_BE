from fastapi import FastAPI, Request
from contextlib import asynccontextmanager
from app.client import http_client
from app.client.mongodb_client import close_mongodb, init_mongodb
from app.client.qdrant_client import check_qdrant_connection, ensure_ingest_collection
from app.config import eureka_config
from app.messaging import kafka_producer
from app.controller import chat_controller, ingest_controller
from app.service.graph_manager import close_checkpointer
from app.config.app_config import settings
from app.dto.response.api_response import ApiResponse
from app.exception.handlers import register_exception_handlers
from app.i18n import resolve_locale, reset_request_locale, set_request_locale
import uvicorn
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup logic
    logger.info(f"Starting {settings.app_name} on port {settings.app_port}...")
    await init_mongodb()
    await http_client.init_http_client()
    await kafka_producer.init_kafka()
    await check_qdrant_connection()
    await ensure_ingest_collection()
    await eureka_config.init_eureka()
    yield
    # Shutdown logic
    logger.info(f"Shutting down {settings.app_name}...")
    await eureka_config.stop_eureka()
    await kafka_producer.stop_kafka()
    await http_client.stop_http_client()
    await close_checkpointer()
    await close_mongodb()

app = FastAPI(title=settings.app_name, lifespan=lifespan)
register_exception_handlers(app)


@app.middleware("http")
async def locale_middleware(request: Request, call_next):
    locale = resolve_locale(request.headers.get("Accept-Language"))
    token = set_request_locale(locale)
    try:
        return await call_next(request)
    finally:
        reset_request_locale(token)

# Health check endpoint
@app.get("/health", response_model=ApiResponse[dict[str, str]])
async def health():
    return ApiResponse.success({"status": "UP"})

# Include routers
app.include_router(chat_controller.router, prefix="/api/v1/ai", tags=["AI"])
app.include_router(ingest_controller.router, prefix="/api/v1/ai", tags=["Ingest"])

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.app_port, reload=True)
