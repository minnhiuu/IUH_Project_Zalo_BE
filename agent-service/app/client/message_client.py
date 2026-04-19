from app.client import http_client
from app.config.app_config import settings
from app.security.security_context import get_user_context
import logging

logger = logging.getLogger(__name__)

async def get_messages_since(conversation_id: str, since_id: str):
    try:
        curr = get_user_context()
        
        headers = {
            "X-Account-Id": str(curr.get("account_id") or ""),
            "X-User-Id": str(curr.get("user_id") or ""),
        }
        
        if curr.get('raw_token'):
            headers["Authorization"] = f"Bearer {curr.get('raw_token')}"

        url = f"{settings.api_gateway_url}/api/messages/internal/conversations/{conversation_id}/messages-since"
        params = {"sinceId": since_id}
        
        logger.info(f"Calling internal message-service via Gateway: {url} since {since_id}")
        
        client = http_client.get_client()
        resp = await client.get(url, params=params, headers=headers)
        resp.raise_for_status()
        
        return resp.json().get("data", [])
    except Exception as e:
        logger.error(f"Failed to fetch messages from message-service: {e}")
        return []
