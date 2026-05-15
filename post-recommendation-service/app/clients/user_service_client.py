import logging
import httpx

from app.core.config import get_settings

logger = logging.getLogger(__name__)

async def get_user_profile(user_id: str) -> dict | None:
    """
    Fetch the user profile (UserSyncResponse) from user-service.
    Returns a dict with user data or None if failed.
    """
    settings = get_settings()
    url = f"{settings.user_service_url}/internal/users/{user_id}"
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url)
            response.raise_for_status()
            data = response.json()
            payload = data.get("data")
            if payload:
                return payload
            logger.warning(
                "user-service returned no data for user_id=%s: %s",
                user_id,
                data,
            )
            return None
    except Exception:
        logger.exception("Failed to fetch user profile from user-service for user_id=%s", user_id)
        return None

async def get_users_batch(last_id: str | None = None, size: int = 500) -> list[dict]:
    """
    Fetch a batch of user profiles from user-service.
    """
    settings = get_settings()
    url = f"{settings.user_service_url}/internal/users/batch"
    params = {"size": size}
    if last_id:
        params["lastId"] = last_id
        
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            data = response.json()
            payload = data.get("data")
            if isinstance(payload, list):
                return payload
            logger.warning("user-service returned unexpected payload for batch: %s", data)
            return []
    except Exception:
        logger.exception("Failed to fetch user batch from user-service")
        return []
