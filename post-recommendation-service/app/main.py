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

settings = get_settings()
setup_logging(settings.log_level)
logger = logging.getLogger(__name__)


def _instance_host() -> str:
    if settings.eureka_instance_host:
        return settings.eureka_instance_host
    if settings.eureka_instance_ip:
        return settings.eureka_instance_ip
    return socket.gethostbyname(socket.gethostname())


def _register_eureka() -> None:
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

    eureka_client.init(
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


def _unregister_eureka() -> None:
    if not settings.eureka_enabled:
        return

    try:
        eureka_client.stop()
        logger.info("Unregistered from Eureka")
    except Exception:
        logger.exception("Error while unregistering from Eureka")


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        _register_eureka()
    except Exception:
        logger.exception("Failed to register to Eureka. Service will continue running.")
    yield
    _unregister_eureka()


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    debug=settings.debug,
    lifespan=lifespan,
)
configure_security(app, settings)
register_exception_handlers(app)
app.include_router(api_router, prefix=settings.api_v1_prefix)
