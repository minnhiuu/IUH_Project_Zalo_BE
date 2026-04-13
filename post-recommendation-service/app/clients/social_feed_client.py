"""Async HTTP client for the social-feed-service internal API."""

import logging
from typing import Any

import httpx

from app.core.config import get_settings

logger = logging.getLogger(__name__)

_settings = get_settings()


async def get_newest_interactions(user_id: str, limit: int) -> list[dict[str, Any]]:
    """
    Call GET /internal/interactions/users/{user_id}/newest on the social-feed-service
    and return the list of interaction dicts from the response body.

    Returns an empty list on any HTTP or connectivity error.
    """
    url = (
        f"{_settings.social_feed_service_url}"
        f"/internal/interactions/users/{user_id}/newest"
    )
    params = {"limit": limit}

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            body: dict[str, Any] = response.json()
            return body.get("data") or []
    except httpx.HTTPStatusError as exc:
        logger.warning(
            "social-feed-service returned %s for user_id=%s: %s",
            exc.response.status_code,
            user_id,
            exc.response.text,
        )
        return []
    except Exception:
        logger.exception(
            "Failed to fetch interactions from social-feed-service for user_id=%s", user_id
        )
        return []


async def get_posts_by_authors(
    author_ids: list[str],
    post_type: str | None = None,
    limit: int = 10,
) -> list[dict[str, Any]]:
    """
    Call ``GET /internal/posts/by-authors`` on the social-feed-service and
    return the raw list of ``PostResponse`` dicts.

    This endpoint is used by the recommendation pipeline to populate the
    ``friend_posts`` and ``peer_posts`` candidate streams.

    Args:
        author_ids: List of user IDs whose recent posts are requested.
            Passed as a repeated ``authorIds`` query parameter.
        post_type: Optional PostType filter (``"FEED"``, ``"SHARE"``,
            ``"REEL"``).  When ``None``, the server returns all active types.
        limit: Maximum posts per author to return (server caps at 50).

    Returns:
        List of raw ``PostResponse``-shaped dicts.  Empty on any error.
    """
    if not author_ids:
        return []

    url = f"{_settings.social_feed_service_url}/internal/posts/by-authors"
    params: dict[str, Any] = {
        "authorIds": author_ids,
        "limit": limit,
    }
    if post_type:
        params["postType"] = post_type

    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            body: dict[str, Any] = response.json()
            posts: list[dict[str, Any]] = body.get("data") or []
            logger.debug(
                "social-feed-service returned %d posts for %d authors (type=%s)",
                len(posts), len(author_ids), post_type,
            )
            return posts
    except httpx.HTTPStatusError as exc:
        logger.warning(
            "social-feed-service /internal/posts/by-authors returned %s: %s",
            exc.response.status_code,
            exc.response.text,
        )
        return []
    except Exception:
        logger.exception("Failed to fetch posts by authors from social-feed-service")
        return []


async def get_view_interactions(user_id: str, limit: int = 200) -> set[str]:
    """Fetch the set of post IDs already viewed by *user_id*.

    Calls ``GET /internal/interactions/users/{user_id}/newest`` on the
    social-feed-service, then filters to ``interactionType == "VIEW"`` records.

    Used by :class:`~app.services.feed_candidate_service.FeedCandidateService`
    to build the ``exclude_ids`` set passed to the RRF engine so already-seen
    posts are not shown again.

    Args:
        user_id: The requesting user's ID.
        limit: Maximum interactions to fetch (default 200).

    Returns:
        Set of viewed post ID strings.  Empty set on any error.
    """
    url = (
        f"{_settings.social_feed_service_url}"
        f"/internal/interactions/users/{user_id}/newest"
    )
    params = {"limit": limit}

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            body: dict[str, Any] = response.json()
            interactions: list[dict[str, Any]] = body.get("data") or []
            viewed: set[str] = {
                ia["postId"]
                for ia in interactions
                if ia.get("interactionType") == "VIEW" and ia.get("postId")
            }
            logger.debug(
                "get_view_interactions: user_id=%s fetched %d interactions, %d views",
                user_id, len(interactions), len(viewed),
            )
            return viewed
    except httpx.HTTPStatusError as exc:
        logger.warning(
            "social-feed-service returned %s for view interactions user_id=%s: %s",
            exc.response.status_code, user_id, exc.response.text,
        )
        return set()
    except Exception:
        logger.exception(
            "Failed to fetch view interactions from social-feed-service for user_id=%s",
            user_id,
        )
        return set()
