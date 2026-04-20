"""
Kafka consumer that processes POST_DISLIKE_RECORDED events.

Listens on ``social-feed.post.dislike.recorded``.  Each message is a
``UserInteractionEvent`` produced by the social-feed-service when a user
marks a post as "not interested".

For every dislike event:
1. Store the post in the ``disliked_posts`` MongoDB collection (hard filter).
2. Apply the negative gradient to the user's interest vector:
       U_new = Normalize(U_old - η · I_disliked)
   This nudges the user vector *away* from the disliked content so that
   future semantic recommendations naturally avoid similar posts.
"""

import asyncio
import json
import logging

from aiokafka import AIOKafkaConsumer

from app.clients.mongodb_client import get_mongodb_database
from app.core.config import get_settings
from app.repositories import recommendation_repository
from app.services import dynamic_vector_service

logger = logging.getLogger(__name__)


class PostDislikeConsumerWorker:
    def __init__(self) -> None:
        self._settings = get_settings()
        self._task: asyncio.Task[None] | None = None
        self._shutdown_event = asyncio.Event()

    def start(self) -> None:
        if not self._settings.kafka_enabled:
            logger.info("Kafka consumer disabled by configuration — PostDislikeConsumerWorker not started")
            return

        if self._task is not None and not self._task.done():
            logger.info("PostDislikeConsumerWorker is already running")
            return

        self._shutdown_event.clear()
        self._task = asyncio.create_task(self._run(), name="post-dislike-consumer")
        logger.info(
            "Started PostDislikeConsumerWorker for topic: %s",
            self._settings.kafka_post_dislike_recorded_topic,
        )

    async def stop(self) -> None:
        if self._task is None:
            return

        self._shutdown_event.set()
        await self._task
        self._task = None
        logger.info("Stopped PostDislikeConsumerWorker")

    async def _run(self) -> None:
        consumer = AIOKafkaConsumer(
            self._settings.kafka_post_dislike_recorded_topic,
            bootstrap_servers=self._settings.kafka_bootstrap_servers,
            group_id=self._settings.kafka_dislike_consumer_group_id,
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

                for records in messages.values():
                    for message in records:
                        await self._handle_event(message.value)

        except Exception:
            logger.exception("PostDislikeConsumerWorker crashed")
        finally:
            await consumer.stop()

    async def _handle_event(self, event: dict) -> None:
        user_id = event.get("userId") or event.get("user_id")
        post_id = event.get("postId") or event.get("post_id")

        if not user_id or not post_id:
            logger.warning("Dislike event missing userId or postId: %s", event)
            return

        user_id = str(user_id)
        post_id = str(post_id)

        # 1. Store dislike in MongoDB for hard filtering
        try:
            db = get_mongodb_database()
            await recommendation_repository.mark_post_disliked(db, user_id, post_id)
            logger.info("Stored dislike: user_id=%s, post_id=%s", user_id, post_id)
        except Exception:
            logger.exception("Failed to store dislike for user_id=%s, post_id=%s", user_id, post_id)

        # 2. Apply negative feedback to user vector: U_new = Normalize(U_old - η · I_disliked)
        try:
            updated = await dynamic_vector_service.apply_negative_feedback(user_id, post_id)
            if updated:
                logger.info("Negative feedback applied: user_id=%s, post_id=%s", user_id, post_id)
            else:
                logger.debug(
                    "Negative feedback not applied (no vector/post found): user_id=%s, post_id=%s",
                    user_id, post_id,
                )
        except Exception:
            logger.exception(
                "Unhandled error applying negative feedback: user_id=%s, post_id=%s",
                user_id, post_id,
            )
