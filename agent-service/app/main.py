from fastapi import FastAPI
from contextlib import asynccontextmanager
from app.client import http_client
from app.config import eureka_config
from app.messaging import kafka_producer
from app.controller import chat_controller
from app.config.app_config import settings
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
    await http_client.init_http_client()
    await kafka_producer.init_kafka()
    await eureka_config.init_eureka()
    yield
    # Shutdown logic
    logger.info(f"Shutting down {settings.app_name}...")
    await eureka_config.stop_eureka()
    await kafka_producer.stop_kafka()
    await http_client.stop_http_client()

app = FastAPI(title=settings.app_name, lifespan=lifespan)

# Health check endpoint
@app.get("/health")
async def health():
    return {"status": "UP"}

# Include routers
app.include_router(chat_controller.router, prefix="/api/v1/ai", tags=["AI"])

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.app_port, reload=True)
