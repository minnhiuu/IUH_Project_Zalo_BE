import asyncio
import json
import logging
from dataclasses import dataclass
from datetime import UTC, datetime
from uuid import NAMESPACE_URL, UUID, uuid5

from aiokafka import AIOKafkaConsumer

from app.clients.qdrant_client import get_qdrant_client
from app.core.config import get_settings
from app.services.user_vectorizer import build_baseline_user_document, user_profile_from_event

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class UserTopicBindings:
    user_created: str


class UserEventConsumerWorker:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._topics = UserTopicBindings(
            user_created=self._settings.kafka_user_created_topic,
        )
        self._task: asyncio.Task[None] | None = None
        self._shutdown_event = asyncio.Event()

    def start(self) -> None:
        if not self._settings.kafka_enabled:
            logger.info("Kafka consumer disabled by configuration")
            return

        if self._task is not None and not self._task.done():
            logger.info("UserEventConsumerWorker is already running")
            return

        self._shutdown_event.clear()
        self._task = asyncio.create_task(self._run(), name="user-event-consumer")
        logger.info(
            "Started UserEventConsumerWorker for topic: %s",
            self._topics.user_created,
        )

    async def stop(self) -> None:
        if self._task is None:
            return

        self._shutdown_event.set()
        await self._task
        self._task = None
        logger.info("Stopped UserEventConsumerWorker")

    async def _run(self) -> None:
        consumer = AIOKafkaConsumer(
            self._topics.user_created,
            bootstrap_servers=self._settings.kafka_bootstrap_servers,
            group_id=self._settings.kafka_user_consumer_group_id,
            enable_auto_commit=True,
            auto_offset_reset="earliest",
            value_deserializer=lambda value: json.loads(value.decode("utf-8")),
        )

        qdrant_client = get_qdrant_client()

        try:
            qdrant_client.set_model(self._settings.embedding_model_name)
            await consumer.start()
            self._ensure_collection_exists(qdrant_client)

            while not self._shutdown_event.is_set():
                messages = await consumer.getmany(timeout_ms=1000, max_records=100)
                if not messages:
                    continue

                for records in messages.values():
                    for message in records:
                        await self._process_message(
                            qdrant_client=qdrant_client,
                            topic=message.topic,
                            event=message.value,
                        )

        except Exception:
            logger.exception("UserEventConsumerWorker crashed")
        finally:
            await consumer.stop()

    def _ensure_collection_exists(self, qdrant_client) -> None:
        collection_name = self._settings.qdrant_user_collection_name
        existing = qdrant_client.collection_exists(collection_name=collection_name)
        if existing:
            return

        qdrant_client.create_collection(
            collection_name=collection_name,
            vectors_config=qdrant_client.get_fastembed_vector_params(),
        )
        logger.info(
            "Created Qdrant user collection '%s' with vector settings from model '%s'",
            collection_name,
            self._settings.embedding_model_name,
        )

    async def _process_message(self, *, qdrant_client, topic: str, event: dict) -> None:
        user_id = event.get("userId") or event.get("id")
        if not user_id:
            logger.warning("Skipping user event without userId: %s", event)
            return

        profile = user_profile_from_event(event)
        semantic_text = build_baseline_user_document(profile)
        qdrant_point_id = self._to_qdrant_point_id(user_id)

        payload = {
            "user_id": user_id,
            "account_id": event.get("accountId"),
            "full_name": profile.fullName,
            "bio": profile.bio,
            "initial_interests": profile.initialInterests,
            "dob": profile.dob.isoformat() if profile.dob else None,
            "gender": profile.gender,
            "indexed_at": datetime.now(UTC).isoformat(),
            "event_topic": topic,
        }

        qdrant_client.add(
            collection_name=self._settings.qdrant_user_collection_name,
            documents=[semantic_text],
            metadata=[payload],
            ids=[qdrant_point_id],
        )
        logger.info("Upserted user vector in Qdrant: topic=%s, user_id=%s", topic, user_id)

    def _to_qdrant_point_id(self, user_id: str) -> str:
        try:
            UUID(user_id)
            return user_id
        except (ValueError, TypeError):
            return str(uuid5(NAMESPACE_URL, str(user_id)))