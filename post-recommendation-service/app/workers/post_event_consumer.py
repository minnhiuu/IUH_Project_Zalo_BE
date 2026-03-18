import asyncio
import json
import logging
from dataclasses import dataclass
from datetime import UTC, datetime
from uuid import NAMESPACE_URL, UUID, uuid5

from aiokafka import AIOKafkaConsumer
from qdrant_client.http.models import PointIdsList

from app.clients.qdrant_client import get_qdrant_client
from app.core.config import get_settings
from app.services.post_vectorizer import prepare_post_text

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class TopicBindings:
    post_created: str
    post_updated: str
    post_deleted: str


class PostEventConsumerWorker:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._topics = TopicBindings(
            post_created=self._settings.kafka_post_created_topic,
            post_updated=self._settings.kafka_post_updated_topic,
            post_deleted=self._settings.kafka_post_deleted_topic,
        )
        self._task: asyncio.Task[None] | None = None
        self._shutdown_event = asyncio.Event()

    def start(self) -> None:
        if not self._settings.kafka_enabled:
            logger.info("Kafka consumer disabled by configuration")
            return

        if self._task is not None and not self._task.done():
            logger.info("PostEventConsumerWorker is already running")
            return

        self._shutdown_event.clear()
        self._task = asyncio.create_task(self._run(), name="post-event-consumer")
        logger.info(
            "Started PostEventConsumerWorker for topics: %s, %s, %s",
            self._topics.post_created,
            self._topics.post_updated,
            self._topics.post_deleted,
        )

    async def stop(self) -> None:
        if self._task is None:
            return

        self._shutdown_event.set()
        await self._task
        self._task = None
        logger.info("Stopped PostEventConsumerWorker")

    async def _run(self) -> None:
        consumer = AIOKafkaConsumer(
            self._topics.post_created,
            self._topics.post_updated,
            self._topics.post_deleted,
            bootstrap_servers=self._settings.kafka_bootstrap_servers,
            group_id=self._settings.kafka_consumer_group_id,
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
            logger.exception("PostEventConsumerWorker crashed")
        finally:
            await consumer.stop()

    def _ensure_collection_exists(self, qdrant_client) -> None:
        collection_name = self._settings.qdrant_collection_name
        existing = qdrant_client.collection_exists(collection_name=collection_name)
        if existing:
            return

        qdrant_client.create_collection(
            collection_name=collection_name,
            vectors_config=qdrant_client.get_fastembed_vector_params(),
        )
        logger.info(
            "Created Qdrant collection '%s' with vector settings from model '%s'",
            collection_name,
            self._settings.embedding_model_name,
        )

    async def _process_message(self, *, qdrant_client, topic: str, event: dict) -> None:
        post_id = event.get("post_id")
        if not post_id:
            logger.warning("Skipping post event without post_id: %s", event)
            return
        qdrant_point_id = self._to_qdrant_point_id(post_id)

        if topic == self._topics.post_deleted:
            qdrant_client.delete(
                collection_name=self._settings.qdrant_collection_name,
                points_selector=PointIdsList(points=[qdrant_point_id]),
            )
            logger.info("Deleted post vector from Qdrant: post_id=%s", post_id)
            return

        title = event.get("title")
        caption = event.get("caption")
        description = event.get("description")

        hashtags = event.get("hashtags")
        if hashtags is None and isinstance(event.get("content"), dict):
            hashtags = event["content"].get("hashtags", [])
        if hashtags is None:
            hashtags = []

        content = {
            "title": title,
            "caption": caption,
            "description": description,
            "hashtags": hashtags,
        }
        semantic_text = prepare_post_text(content)
        if not semantic_text:
            semantic_text = "Content: empty post"

        payload = {
            "post_id": post_id,
            "author_id": event.get("author_id"),
            "group_id": event.get("group_id"),
            "post_type": event.get("post_type") or event.get("postType"),
            "title": title,
            "caption": caption,
            "description": description,
            "hashtags": hashtags,
            "visibility": event.get("visibility"),
            "stats": event.get("stats", {}),
            "uploaded_at": event.get("uploaded_at"),
            "updated_at": event.get("updated_at"),
            "indexed_at": datetime.now(UTC).isoformat(),
        }

        qdrant_client.add(
            collection_name=self._settings.qdrant_collection_name,
            documents=[semantic_text],
            metadata=[payload],
            ids=[qdrant_point_id],
        )
        logger.info("Upserted post vector in Qdrant: topic=%s, post_id=%s", topic, post_id)

    def _to_qdrant_point_id(self, post_id: str) -> str:
        try:
            UUID(post_id)
            return post_id
        except (ValueError, TypeError):
            # Keep stable mapping from source post IDs to valid Qdrant UUID IDs.
            return str(uuid5(NAMESPACE_URL, str(post_id)))
