"""
FeedCandidateService — assembles the five candidate streams for the RRF engine.

Responsibility
--------------
This service is the single point of HTTP orchestration in the recommendation
pipeline.  It fetches raw post candidates from the social-feed-service and
(optionally) friend/peer IDs from the friend-service, then hands a ready-made
``sources`` dict to ``RRFRecommendationEngine.get_final_feed``.

Stream construction
-------------------

    Source              How obtained
    ------------------- --------------------------------------------------
    user_vector         Provided by caller (Qdrant ANN results)
    peer_posts          Posts authored by Interest Peers (Qdrant neighbours)
    friend_posts        Posts authored by the user's accepted friends
    trending_posts      Latest posts of the target type (global popularity)
    random_posts        Provided by caller (random pool from DB/Qdrant)

The ``friend_posts`` stream is the main addition managed by this service:

    1. ``friend_service_client.get_friend_ids``  — GET /friendships/friends
    2. ``social_feed_client.get_posts_by_authors`` — GET /internal/posts/by-authors

Both calls are made concurrently.  Any failure is caught and returns an empty
list so the engine degrades gracefully to the remaining four sources.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from app.clients import friend_service_client, social_feed_client
from app.clients.social_feed_client import get_view_interactions
from app.clients.mongodb_client import get_mongodb_database
from app.core.config import get_settings
from app.repositories import recommendation_repository
from app.services.rrf_recommendation_engine import (
    SOURCE_FRIEND_POSTS,
    SOURCE_PEER_POSTS,
    SOURCE_RANDOM,
    SOURCE_TRENDING,
    SOURCE_USER_VECTOR,
    PostTypeFlow,
    RRFRecommendationEngine,
)

logger = logging.getLogger(__name__)
_settings = get_settings()

# How many posts to fetch per source from the social-feed-service
_FRIEND_POST_LIMIT: int = 30   # per friend (server will aggregate)
_PEER_POST_LIMIT: int = 20
_TRENDING_LIMIT: int = 100
_FRIEND_LIMIT: int = 50        # max friends to fetch


def _post_type_filter(flow: PostTypeFlow) -> str | None:
    """Map a PostTypeFlow to the single postType string accepted by the
    social-feed-service internal API, or None to fetch all types."""
    if flow == PostTypeFlow.REEL:
        return "REEL"
    # FEED_SHARE — the server endpoint accepts a list; we pass None and
    # let generate_source_ranks filter on the Python side.
    return None


def _normalize_post(raw: dict[str, Any], initial_score: float = 1.0) -> dict[str, Any]:
    """Convert a ``PostResponse`` dict from social-feed-service into the
    flat dict expected by ``RRFRecommendationEngine.generate_source_ranks``.

    Required keys: ``id``, ``created_at``, ``initial_score``, ``post_type``.
    """
    stats: dict[str, Any] = raw.get("stats") or {}

    # Derive an initial_score from popularity stats when no explicit score is
    # given (e.g. for friend/trending posts that don't come from Qdrant).
    if initial_score == 1.0:
        views = max(0, int(stats.get("viewCount") or 0))
        reactions = max(0, int(stats.get("reactionCount") or 0))
        # Simple log-scaled proxy; keeps scores in a comparable range to Qdrant cosine scores
        import math
        initial_score = 0.5 + 0.3 * math.log1p(views) + 0.2 * math.log1p(reactions)

    return {
        "id": raw.get("id", ""),
        "created_at": str(raw.get("uploadedAt") or raw.get("createdAt") or ""),
        "initial_score": initial_score,
        "post_type": str((raw.get("postType") or "")).upper(),
        # passthrough extras that the engine will surface in output dicts
        "author_id": (raw.get("authorInfo") or {}).get("id"),
        "group_id": raw.get("groupId"),
        "stats": stats,
        "content": raw.get("content"),
        "media": raw.get("media") or [],
        "visibility": raw.get("visibility"),
    }


class FeedCandidateService:
    """Orchestrates candidate retrieval for the RRF recommendation engine.

    Args:
        engine: A configured ``RRFRecommendationEngine`` instance.  Shared and
            injected at startup so callers don't have to construct it.
    """

    def __init__(self, engine: RRFRecommendationEngine) -> None:
        self._engine = engine

    async def build_feed(
        self,
        user_id: str,
        user_vector_candidates: list[dict[str, Any]],
        peer_user_ids: list[str],
        random_candidates: list[dict[str, Any]],
        flow: PostTypeFlow = PostTypeFlow.FEED_SHARE,
        n: int = 20,
        random_seed: int | None = None,
    ) -> list[str]:
        """Assemble all five candidate streams and return a ranked feed.

        The method runs the two network-bound fetches (friend IDs + posts by
        authors) concurrently with the trending fetch, then delegates ranking
        to the engine.

        Args:
            user_id: The requesting user's ID (used to query their friend list).
            user_vector_candidates: Already-fetched Qdrant ANN results for this
                user, as raw dicts with ``id``, ``created_at``, ``initial_score``,
                and ``post_type``.  These form the ``user_vector`` source.
            peer_user_ids: IDs of Interest Peers (from Qdrant user-vector ANN).
                Used to fetch the ``peer_posts`` source.
            random_candidates: Pre-sampled random post pool (e.g., from MongoDB).
                Forms the ``random_posts`` source unchanged.
            flow: Target post-type flow.
            n: Desired feed length.
            random_seed: Optional seed for deterministic random injection.

        Returns:
            Ranked list of post ID strings from ``RRFRecommendationEngine.get_final_feed``.
        """
        post_type_filter = _post_type_filter(flow)

        # ── Concurrent network fetches ─────────────────────────────────────────
        # Fetch viewed IDs first so each source can over-fetch to compensate
        # for excluded posts and still return the full desired limit.
        viewed_post_ids = await get_view_interactions(user_id, limit=200)

        # Also fetch permanently disliked posts to exclude from all sources
        disliked_post_ids = await recommendation_repository.get_disliked_post_ids(
            get_mongodb_database(), user_id
        )
        all_excluded_ids = viewed_post_ids | disliked_post_ids

        friend_posts_raw, peer_posts_raw, trending_raw = await asyncio.gather(
            self._fetch_friend_posts(user_id, post_type_filter, all_excluded_ids),
            self._fetch_peer_posts(peer_user_ids, post_type_filter, all_excluded_ids),
            self._fetch_trending_posts(post_type_filter, all_excluded_ids),
            return_exceptions=False,
        )

        # ── Assemble sources dict ──────────────────────────────────────────────
        sources: dict[str, list[dict[str, Any]]] = {
            SOURCE_USER_VECTOR:  user_vector_candidates,
            SOURCE_PEER_POSTS:   [_normalize_post(p) for p in peer_posts_raw],
            SOURCE_FRIEND_POSTS: [_normalize_post(p) for p in friend_posts_raw],
            SOURCE_TRENDING:     [_normalize_post(p) for p in trending_raw],
            SOURCE_RANDOM:       random_candidates,
        }

        logger.info(
            "feed_candidate_service: user_id=%s flow=%s counts: uv=%d peer=%d friend=%d trend=%d rand=%d",
            user_id,
            flow.value,
            len(sources[SOURCE_USER_VECTOR]),
            len(sources[SOURCE_PEER_POSTS]),
            len(sources[SOURCE_FRIEND_POSTS]),
            len(sources[SOURCE_TRENDING]),
            len(sources[SOURCE_RANDOM]),
        )

        result = self._engine.get_final_feed(
            sources,
            n=n,
            flow=flow,
            random_seed=random_seed,
            exclude_ids=all_excluded_ids,
        )

        # Fallback: if exclusion emptied the pool, serve the best posts anyway
        # so the user never sees a blank feed.
        if not result and all_excluded_ids:
            logger.info(
                "feed_candidate_service: all candidates excluded for user_id=%s — "
                "falling back to unfiltered RRF result (preserving dislike exclusions)",
                user_id,
            )
            # Keep disliked posts excluded even in fallback — only drop view exclusion
            result = self._engine.get_final_feed(
                sources,
                n=n,
                flow=flow,
                random_seed=random_seed,
                exclude_ids=disliked_post_ids if disliked_post_ids else None,
            )

        return result

    # ── Private fetch helpers ──────────────────────────────────────────────────

    async def _fetch_friend_posts(
        self,
        user_id: str,
        post_type_filter: str | None,
        exclude_ids: set[str],
    ) -> list[dict[str, Any]]:
        """Two-step: get friend IDs → get their posts, over-fetching to keep
        *_FRIEND_POST_LIMIT* usable posts after client-side exclusion."""
        try:
            friend_ids = await friend_service_client.get_friend_ids(
                user_id, limit=_FRIEND_LIMIT
            )
        except Exception:
            logger.exception("get_friend_ids failed for user_id=%s", user_id)
            return []

        if not friend_ids:
            return []

        # Ask for extra posts so that after filtering viewed ones we still have
        # approximately _FRIEND_POST_LIMIT usable candidates.
        fetch_limit = _FRIEND_POST_LIMIT + len(exclude_ids)
        try:
            posts = await social_feed_client.get_posts_by_authors(
                author_ids=friend_ids,
                post_type=post_type_filter,
                limit=fetch_limit,
            )
            # Filter client-side and preserve up to the desired limit.
            filtered = [p for p in posts if p.get("id") not in exclude_ids]
            return filtered[:_FRIEND_POST_LIMIT]
        except Exception:
            logger.exception("get_posts_by_authors (friends) failed for user_id=%s", user_id)
            return []

    async def _fetch_peer_posts(
        self,
        peer_user_ids: list[str],
        post_type_filter: str | None,
        exclude_ids: set[str],
    ) -> list[dict[str, Any]]:
        """Fetch recent posts from Interest Peers, over-fetching to keep
        *_PEER_POST_LIMIT* usable posts after client-side exclusion."""
        if not peer_user_ids:
            return []
        fetch_limit = _PEER_POST_LIMIT + len(exclude_ids)
        try:
            posts = await social_feed_client.get_posts_by_authors(
                author_ids=peer_user_ids,
                post_type=post_type_filter,
                limit=fetch_limit,
            )
            filtered = [p for p in posts if p.get("id") not in exclude_ids]
            return filtered[:_PEER_POST_LIMIT]
        except Exception:
            logger.exception("get_posts_by_authors (peers) failed")
            return []

    async def _fetch_trending_posts(
        self,
        post_type_filter: str | None,
        exclude_ids: set[str],
    ) -> list[dict[str, Any]]:
        """Fetch globally-trending posts, over-fetching to keep
        *_TRENDING_LIMIT* usable posts after client-side exclusion."""
        fetch_limit = _TRENDING_LIMIT + len(exclude_ids)
        try:
            posts = await social_feed_client.get_posts_by_authors(
                author_ids=[],      # empty = no author filter (all posts)
                post_type=post_type_filter,
                limit=fetch_limit,
            )
            filtered = [p for p in posts if p.get("id") not in exclude_ids]
            return filtered[:_TRENDING_LIMIT]
        except Exception:
            logger.exception("get_posts_by_authors (trending) failed")
            return []
