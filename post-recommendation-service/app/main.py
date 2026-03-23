import logging
import os
import socket
from contextlib import asynccontextmanager

import py_eureka_client.eureka_client as eureka_client
from fastapi import FastAPI

from app.api.v1.router import api_router
from app.core.config import get_settings
from app.core.exception_handlers import register_exception_handlers
from app.core.logging import setup_logging
from app.security.security_config import configure_security
from app.workers.post_event_consumer import PostEventConsumerWorker
from app.workers.user_event_consumer import UserEventConsumerWorker
from app.workers.user_interaction_consumer import UserInteractionConsumerWorker
from app.clients.mongodb_client import get_mongodb_database
from app.repositories import recommendation_repository

settings = get_settings()
setup_logging(settings.log_level)
logger = logging.getLogger(__name__)
post_event_consumer = PostEventConsumerWorker()
user_event_consumer = UserEventConsumerWorker()
user_interaction_consumer = UserInteractionConsumerWorker()


def _instance_host() -> str:
    if settings.eureka_instance_host:
        return settings.eureka_instance_host
    if settings.eureka_instance_ip:
        return settings.eureka_instance_ip
    return socket.gethostbyname(socket.gethostname())


async def _register_eureka() -> None:
    if "PYTEST_CURRENT_TEST" in os.environ:
        logger.info("Skipping Eureka registration in test execution")
        return

    if not settings.eureka_enabled:
        logger.info("Eureka registration disabled")
        return

    host = _instance_host()
    instance_id = settings.eureka_instance_id or f"{settings.app_name}:{host}:{settings.port}"

    home_page_url = f"http://{host}:{settings.port}/"
    health_check_url = f"http://{host}:{settings.port}{settings.health_path}"

    await eureka_client.init_async(
        eureka_server=settings.eureka_server_url,
        app_name=settings.app_name,
        instance_host=host,
        instance_port=settings.port,
        instance_id=instance_id,
        home_page_url=home_page_url,
        status_page_url=home_page_url,
        health_check_url=health_check_url,
    )
    logger.info("Registered to Eureka as %s at %s", settings.app_name, settings.eureka_server_url)


async def _unregister_eureka() -> None:
    if not settings.eureka_enabled:
        return

    try:
        await eureka_client.stop_async()
        logger.info("Unregistered from Eureka")
    except Exception:
        logger.exception("Error while unregistering from Eureka")


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        await _register_eureka()
    except Exception:
        logger.exception("Failed to register to Eureka. Service will continue running.")
    try:
        recommendation_repository.ensure_indexes(get_mongodb_database())
        logger.info("Recommendation repository indexes ensured")
    except Exception:
        logger.exception("Failed to ensure recommendation repository indexes")
    post_event_consumer.start()
    user_event_consumer.start()
    user_interaction_consumer.start()
    yield
    await post_event_consumer.stop()
    await user_event_consumer.stop()
    await user_interaction_consumer.stop()
    await _unregister_eureka()


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    debug=settings.debug,
    lifespan=lifespan,
)
configure_security(app, settings)
register_exception_handlers(app)
app.include_router(api_router, prefix=settings.api_v1_prefix)
