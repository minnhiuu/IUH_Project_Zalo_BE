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
