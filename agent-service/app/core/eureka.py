import py_eureka_client.eureka_client as eureka_client
from app.core.config import settings
import logging

logger = logging.getLogger(__name__)

async def init_eureka():
    try:
        await eureka_client.init_async(
            eureka_server=settings.eureka_server,
            app_name=settings.app_name,
            instance_port=settings.app_port,
            instance_host=settings.eureka_instance_host
        )
        logger.info(f"Successfully registered with Eureka at {settings.eureka_server}")
    except Exception as e:
        logger.error(f"Failed to register with Eureka: {e}")

async def stop_eureka():
    try:
        await eureka_client.stop_async()
        logger.info("Successfully unregistered from Eureka")
    except Exception as e:
        logger.error(f"Error during Eureka unregistration: {e}")
