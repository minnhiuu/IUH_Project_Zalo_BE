from langchain_core.tools import tool
from app.integration.user_service import get_my_profile as get_profile_api, update_bio as update_bio_api
from app.core.security import get_user_context
import logging

logger = logging.getLogger(__name__)

@tool
async def get_my_profile():
    """Lấy thông tin cá nhân của người dùng hiện tại bao gồm tên, email và vai trò."""
    try:
        return await get_profile_api()
    except Exception as e:
        logger.error(f"Error in tool get_my_profile: {e}")
        return {"error": str(e)}

@tool
async def update_my_bio(new_bio: str):
    """Cập nhật phần giới thiệu (bio) của người dùng."""
    try:
        return await update_bio_api(new_bio)
    except Exception as e:
        logger.error(f"Error in tool update_my_bio: {e}")
        return {"error": str(e)}

# Export list of tools
tools = [get_my_profile, update_my_bio]
