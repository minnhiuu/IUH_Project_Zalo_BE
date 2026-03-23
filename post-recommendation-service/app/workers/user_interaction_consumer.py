"""
Kafka consumer that triggers user vector updates on interaction events.

Listens on `kafka_user_interaction_topic`. Each message is expected to carry
at minimum a `userId` field.  Multiple messages from one poll cycle that share
the same userId are deduplicated so we only run the (relatively expensive)
vector update once per user per batch.
"""

import asyncio
import json
import logging

from aiokafka import AIOKafkaConsumer

from app.core.config import get_settings
from app.services import dynamic_vector_service

logger = logging.getLogger(__name__)


class UserInteractionConsumerWorker:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._task: asyncio.Task[None] | None = None
        self._shutdown_event = asyncio.Event()

    def start(self) -> None:
        if not self._settings.kafka_enabled:
            logger.info("Kafka consumer disabled by configuration — UserInteractionConsumerWorker not started")
            return

        if self._task is not None and not self._task.done():
            logger.info("UserInteractionConsumerWorker is already running")
            return

        self._shutdown_event.clear()
        self._task = asyncio.create_task(self._run(), name="user-interaction-consumer")
        logger.info(
            "Started UserInteractionConsumerWorker for topic: %s",
            self._settings.kafka_user_interaction_topic,
        )

    async def stop(self) -> None:
        if self._task is None:
            return

        self._shutdown_event.set()
        await self._task
        self._task = None
        logger.info("Stopped UserInteractionConsumerWorker")

    async def _run(self) -> None:
        consumer = AIOKafkaConsumer(
            self._settings.kafka_user_interaction_topic,
            bootstrap_servers=self._settings.kafka_bootstrap_servers,
            group_id=self._settings.kafka_interaction_consumer_group_id,
            enable_auto_commit=True,
            auto_offset_reset="latest",
            value_deserializer=lambda value: json.loads(value.decode("utf-8")),
        )

        try:
            await consumer.start()

            while not self._shutdown_event.is_set():
                messages = await consumer.getmany(timeout_ms=1000, max_records=200)
                if not messages:
                    continue

                # Collect unique user_ids from the batch to avoid redundant refreshes.
                user_ids: set[str] = set()
                for records in messages.values():
                    for message in records:
                        event = message.value
                        user_id = event.get("userId") or event.get("user_id")
                        if user_id:
                            user_ids.add(str(user_id))

                for user_id in user_ids:
                    await self._handle_user(user_id)

        except Exception:
            logger.exception("UserInteractionConsumerWorker crashed")
        finally:
            await consumer.stop()

    async def _handle_user(self, user_id: str) -> None:
        try:
            updated = await dynamic_vector_service.update_user_vector(user_id)
            if not updated:
                logger.debug("Vector not updated for user_id=%s (fallback or no change)", user_id)
        except Exception:
            logger.exception("Unhandled error updating vector for user_id=%s", user_id)
