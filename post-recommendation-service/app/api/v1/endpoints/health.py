from fastapi import APIRouter

from app.common.api_response import ApiResponse

router = APIRouter()


@router.get("/health")
def health_check() -> ApiResponse[dict[str, str]]:
    payload = {"status": "ok", "service": "post-recommendation-service"}
    return ApiResponse.success(payload)
