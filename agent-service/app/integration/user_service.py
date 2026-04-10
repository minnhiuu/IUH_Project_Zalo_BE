from app.core import http_client
from app.core.config import settings
from app.core.security import get_user_context
from app.dtos.user_dto import UserResponse
import logging

logger = logging.getLogger(__name__)

async def get_my_profile() -> UserResponse:
    curr = get_user_context()
    
    headers = {
        "X-Account-Id": str(curr.get("account_id") or ""),
        "X-User-Id": str(curr.get("user_id") or ""),
    }
    
    if curr.get('raw_token'):
        headers["Authorization"] = f"Bearer {curr.get('raw_token')}"

    # Routing through API Gateway: Gateway translates /api/users/me -> user-service /users/me
    service_url = f"{settings.api_gateway_url}/api/users/me"
    
    client = http_client.get_client()
    response = await client.get(service_url, headers=headers)
    response.raise_for_status()
    
    # Extract data from ApiResponse envelope {"status": "success", "data": {...}}
    return UserResponse(**response.json().get("data", {}))

async def update_bio(new_bio: str):
    curr = get_user_context()
    
    headers = {
        "X-Account-Id": str(curr.get("account_id") or ""),
        "X-User-Id": str(curr.get("user_id") or ""),
        "Content-Type": "application/json"
    }
    
    if curr.get('raw_token'):
        headers["Authorization"] = f"Bearer {curr.get('raw_token')}"

    # Gateway translates /api/users/profile/bio -> user-service /users/profile/bio
    service_url = f"{settings.api_gateway_url}/api/users/profile/bio"
    
    client = http_client.get_client()
    response = await client.put(service_url, json={"bio": new_bio}, headers=headers)
    response.raise_for_status()
    
    return response.json().get("data", {})
