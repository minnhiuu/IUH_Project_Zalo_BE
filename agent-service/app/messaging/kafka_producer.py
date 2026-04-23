from aiokafka import AIOKafkaProducer
import json
import logging
from app.config.app_config import settings
from typing import Optional

from app.messaging.event_types import KafkaEventType

logger = logging.getLogger(__name__)

producer: Optional[AIOKafkaProducer] = None

async def init_kafka():
    global producer
    if producer is None:
        try:
            producer = AIOKafkaProducer(
                bootstrap_servers=settings.kafka_bootstrap_servers,
                value_serializer=lambda v: json.dumps(v).encode('utf-8')
            )
            await producer.start()
            logger.info(f"Successfully connected to Kafka at {settings.kafka_bootstrap_servers}")
        except Exception as e:
            logger.error(f"Failed to connect to Kafka: {e}")
            producer = None

async def stop_kafka():
    global producer
    if producer is not None:
        await producer.stop()
        producer = None
        logger.info("Kafka producer stopped")

async def send_event(topic: str, payload: dict, event_type: KafkaEventType):
    """Generic function to send mapped events with TypeId headers."""
    if producer is None:
        logger.error(f"Kafka producer not initialized! Cannot send {event_type.value}")
        return

    try:
        # Lấy giá trị alias định danh (ví dụ: "AiMessageSaveEvent")
        type_id_alias = event_type.value
        headers = [('__TypeId__', type_id_alias.encode('utf-8'))]
        
        await producer.send_and_wait(topic, payload, headers=headers)
        logger.debug(f"Successfully sent {type_id_alias} to {topic}")
    except Exception as e:
        logger.error(f"Failed to send event {event_type.name}: {e}")

async def send_message(topic: str, message: dict):
    """Legacy wrapper for backward compatibility, now uses Type Mapping."""
    await send_event(topic, message, KafkaEventType.AI_MESSAGE_SAVE)

async def send_socket_event(target_user_id: str, event_type: str, destination: str, payload: dict):
    """Refactored to use Type Mapping via SocketEvent alias."""
    event_msg = {
        "type": event_type,
        "targetUserId": target_user_id,
        "destination": destination,
        "payload": payload
    }
    await send_event(settings.socket_events_topic, event_msg, KafkaEventType.SOCKET_NOTIFICATION)
    logger.info(f"Sent socket event {event_type} to user {target_user_id} via Kafka")
