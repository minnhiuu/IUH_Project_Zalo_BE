"""Personalized feed recommendation endpoint."""

import logging

from fastapi import APIRouter, Depends, Query, Request

from app.clients.qdrant_client import get_qdrant_client
from app.clients.mongodb_client import get_mongodb_database
from app.common.api_response import ApiResponse
from app.core.config import get_settings
from app.schemas.recommendation import FeedResponse
from app.security.principal import UserPrincipal
from app.services.recommender_engine import RecommenderEngine

logger = logging.getLogger(__name__)
router = APIRouter()


def _get_user_principal(request: Request) -> UserPrincipal:
    principal: UserPrincipal | None = getattr(request.state, "user_principal", None)
    if principal is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=401, detail="Unauthorized")
    return principal


@router.get(
    "/recommendations/feed",
    summary="Get personalized post feed",
    description=(
        "Returns a personalized, ranked list of posts for the authenticated user "
        "using a 4-stage pipeline: Recall → Filter → Social Signal → RRF Ranking."
    ),
    response_model=ApiResponse[FeedResponse],
)
async def get_personalized_feed(
    request: Request,
    page: int = Query(default=0, ge=0, description="Zero-based page index"),
    page_size: int = Query(default=20, ge=1, le=100, description="Items per page"),
) -> ApiResponse[FeedResponse]:
    """
    Build and return a personalized feed for the authenticated user.

    The user identity is extracted from the JWT (via ``UserPrincipal``
    injected by the security middleware) so no explicit ``user_id`` parameter
    is needed.

    Args:
        request: FastAPI Request (carries UserPrincipal in state).
        page: Zero-based page index for pagination.
        page_size: Number of feed items per page (1–100).

    Returns:
        ``ApiResponse`` wrapping a ``FeedResponse`` with ranked ``FeedItem`` objects.
    """
    principal = _get_user_principal(request)
    user_id: str = principal.user_id or principal.account_id

    engine = RecommenderEngine(
        qdrant=get_qdrant_client(),
        db=get_mongodb_database(),
        settings=get_settings(),
    )

    feed = await engine.get_personalized_feed(
        user_id=user_id,
        page=page,
        page_size=page_size,
    )
    return ApiResponse.success(feed)


@router.get(
    "/internal/recommendations/feed/{user_id}",
    summary="[Internal] Get personalized feed for a user by ID",
    description=(
        "Internal endpoint (no auth required). Used for testing and "
        "service-to-service calls."
    ),
    response_model=ApiResponse[FeedResponse],
)
async def get_personalized_feed_internal(
    user_id: str,
    page: int = Query(default=0, ge=0),
    page_size: int = Query(default=20, ge=1, le=100),
) -> ApiResponse[FeedResponse]:
    """
    Internal version of the feed endpoint — accepts explicit user_id for
    service-to-service calls and local testing.
    """
    engine = RecommenderEngine(
        qdrant=get_qdrant_client(),
        db=get_mongodb_database(),
        settings=get_settings(),
    )

    feed = await engine.get_personalized_feed(
        user_id=user_id,
        page=page,
        page_size=page_size,
    )
    return ApiResponse.success(feed)


@router.post(
    "/internal/recommendations/users/{user_id}/vectorize",
    summary="[Internal] Re-vectorize a user",
    description=(
        "Recomputes the alpha-blended dynamic vector for the given user by "
        "blending their stable baseline vector with a time-decayed centroid of "
        "their recent interactions, then upserts the result back to Qdrant. "
        "Returns whether the vector was updated or fell back to baseline."
    ),
)
async def revectorize_user(user_id: str) -> ApiResponse[dict]:
    """
    Trigger a manual re-vectorization for a specific user.

    Internally calls ``update_user_vector(user_id)`` from
    ``dynamic_vector_service``, which runs the full pipeline:

    1. Fetch baseline vector from Qdrant
    2. Fetch recent interactions from social-feed-service
    3. Compute time-decayed centroid (V_int)
    4. Alpha-blend: V_dynamic = (1-α)·V_base + α·V_int
    5. L2-normalize and upsert back to Qdrant

    Args:
        user_id: The ID of the user whose vector should be refreshed.

    Returns:
        ``ApiResponse`` with ``updated=True`` if the vector was refreshed,
        or ``updated=False`` if the user had no interactions / no baseline.
    """
    from app.services.dynamic_vector_service import update_user_vector

    logger.info("[Vectorize] Manual re-vectorization triggered for user_id=%s", user_id)
    updated: bool = await update_user_vector(user_id)

    result = {
        "user_id": user_id,
        "updated": updated,
        "message": (
            "Vector successfully recomputed and upserted to Qdrant."
            if updated
            else "Vector not updated — no interactions found or no baseline vector exists."
        ),
    }

    logger.info("[Vectorize] Result for user_id=%s: updated=%s", user_id, updated)
    return ApiResponse.success(result)

