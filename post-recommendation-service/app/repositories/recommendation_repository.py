"""
MongoDB repository for the recommendation pipeline.

Collections used
----------------
* ``user_interactions``  (social-feed-service writes; not in this DB — see note below)
* ``seen_posts``         Tracks which posts a user has already viewed/hidden.

Note on cross-service data:
  The ``user_interactions`` collection lives in the **social-feed-service** MongoDB
  database.  The social_feed_client HTTP call is used for interaction data instead
  of a direct DB connection.  This repository only manages data that belongs to the
  post-recommendation-service's own database.
"""

import logging
from datetime import UTC, datetime, timedelta

from pymongo import ASCENDING, DESCENDING
from pymongo.database import Database

logger = logging.getLogger(__name__)

# Collection names
_SEEN_POSTS_COLLECTION = "seen_posts"


# ── Seen / hidden posts ────────────────────────────────────────────────────


async def get_seen_post_ids(db: Database, user_id: str, window_days: int = 30) -> set[str]:
    """
    Return the set of post_ids the user has already seen or hidden within
    the last *window_days* days.

    These are excluded from Stage 1 recall candidates to avoid re-showing
    stale content.

    Args:
        db: The pymongo Database instance.
        user_id: The ID of the user whose seen posts we query.
        window_days: How many days back to look (default 30).

    Returns:
        A set of post_id strings.
    """
    try:
        cutoff = datetime.now(UTC) - timedelta(days=window_days)
        cursor = db[_SEEN_POSTS_COLLECTION].find(
            {"user_id": user_id, "seen_at": {"$gte": cutoff}},
            {"post_id": 1, "_id": 0},
        )
        return {doc["post_id"] for doc in cursor}
    except Exception:
        logger.exception("Failed to fetch seen posts for user_id=%s", user_id)
        return set()


async def mark_post_seen(db: Database, user_id: str, post_id: str) -> None:
    """
    Upsert a seen-post record for the user so it can be filtered out of
    future feeds.

    Args:
        db: The pymongo Database instance.
        user_id: The user who viewed the post.
        post_id: The post that was viewed.
    """
    try:
        db[_SEEN_POSTS_COLLECTION].update_one(
            {"user_id": user_id, "post_id": post_id},
            {"$set": {"seen_at": datetime.now(UTC)}},
            upsert=True,
        )
    except Exception:
        logger.exception(
            "Failed to mark post as seen: user_id=%s, post_id=%s", user_id, post_id
        )


def ensure_indexes(db: Database) -> None:
    """Create indexes on the seen_posts collection (idempotent)."""
    try:
        db[_SEEN_POSTS_COLLECTION].create_index(
            [("user_id", ASCENDING), ("seen_at", DESCENDING)],
            name="idx_user_id_seen_at",
        )
        db[_SEEN_POSTS_COLLECTION].create_index(
            [("user_id", ASCENDING), ("post_id", ASCENDING)],
            name="idx_user_post_unique",
            unique=True,
        )
    except Exception:
        logger.exception("Failed to create recommendation repository indexes")
