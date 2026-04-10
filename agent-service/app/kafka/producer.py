from aiokafka import AIOKafkaProducer
import json
import logging
from app.core.config import settings
from typing import Optional

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

async def send_message(topic: str, message: dict):
    if producer is None:
        logger.warning(f"Kafka producer is not initialized. Cannot send message to {topic}")
        return
    
    try:
        # Spring Boot JsonDeserializer requires __TypeId__ header if no default type is set
        headers = [('__TypeId__', b'com.bondhub.common.event.ai.AiMessageSaveEvent')]
        await producer.send_and_wait(topic, message, headers=headers)
        logger.debug(f"Sent message to {topic}")
    except Exception as e:
        logger.error(f"Error sending message to Kafka topic {topic}: {e}")

async def send_socket_event(target_user_id: str, event_type: str, destination: str, payload: dict):
    """
    Gửi event sang Java socket-service thông qua Kafka
    event_type: MESSAGE, CONVERSATION, PRESENCE, NOTIFICATION
    destination: /queue/messages, /queue/conversations...
    """
    if producer is None:
        logger.warning("Kafka producer is not initialized. Cannot send socket event")
        return

    try:
        # Tạo format y hệt record SocketEvent bên Java
        event_msg = {
            "type": event_type,
            "targetUserId": target_user_id,
            "destination": destination,
            "payload": payload
        }
        # Thêm header cho Spring Boot
        headers = [('__TypeId__', b'com.bondhub.common.dto.client.socketservice.SocketEvent')]
        await producer.send_and_wait(settings.socket_events_topic, event_msg, headers=headers)
        logger.info(f"Sent socket event {event_type} to user {target_user_id} via Kafka")
    except Exception as e:
        logger.error(f"Error sending socket event to Kafka: {e}")
