"""Async HTTP client for the friend-service internal API."""

import logging
from typing import Any

import httpx

from app.core.config import get_settings

logger = logging.getLogger(__name__)

_settings = get_settings()


async def get_friend_ids(user_id: str, limit: int = 50) -> list[str]:
    """
    Fetch the accepted friend list for *user_id* from the friend-service
    internal endpoint and return only the friend user-ID strings.

    Calls ``GET /internal/friendships/friends?userId={user_id}&size={limit}``.
    This endpoint does not require a JWT — it is permitted by the
    friend-service SecurityConfig for all ``/internal/`` paths.

    Args:
        user_id: The ID of the user whose friend list is requested.
        limit: Maximum number of friends to fetch (capped at 200).

    Returns:
        A list of friend user-ID strings.  Empty on any error.
    """
    safe_limit = min(limit, 200)
    url = f"{_settings.friend_service_url}/internal/friendships/friends"
    params: dict[str, Any] = {"userId": user_id, "size": safe_limit}

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            body: dict[str, Any] = response.json()
            # Response: ApiResponse<List<String>>  →  body["data"] is a list of IDs
            friend_ids: list[str] = body.get("data") or []
            logger.debug(
                "friend-service returned %d friends for user_id=%s", len(friend_ids), user_id
            )
            return friend_ids

    except httpx.HTTPStatusError as exc:
        logger.warning(
            "friend-service returned %s for user_id=%s: %s",
            exc.response.status_code,
            user_id,
            exc.response.text,
        )
        return []
    except Exception:
        logger.exception(
            "Failed to fetch friends from friend-service for user_id=%s", user_id
        )
        return []
