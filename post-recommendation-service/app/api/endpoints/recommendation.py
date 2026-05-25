"""Personalized feed recommendation endpoint."""

import logging
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

from fastapi import APIRouter, Depends, HTTPException, Query, Request

from app.clients.qdrant_client import get_qdrant_client
from app.clients.mongodb_client import get_mongodb_database
from app.common.api_response import ApiResponse
from app.core.config import get_settings
from app.schemas.recommendation import FeedResponse
from app.security.principal import UserPrincipal
from app.services.recommender_engine import RecommenderEngine
from app.services.rrf_recommendation_engine import PostTypeFlow, RRFRecommendationEngine
from app.services.feed_candidate_service import FeedCandidateService

from app.services.dynamic_vector_service import compute_global_centroid
from pydantic import BaseModel, Field

class UserInterestSeedUpdateRequest(BaseModel):
    initial_interests: list[str] = Field(alias="initialInterests", default_factory=list)

logger = logging.getLogger(__name__)
router = APIRouter()

_RECALL_LIMIT = 100   # Qdrant post ANN candidates for RRF user_vector source
_PEER_LIMIT = 20      # Interest peers from Qdrant user collection
_VECTOR_NAME = "fast-bge-base-en-v1.5"  # Named vector in Qdrant collections


def _get_user_principal(request: Request) -> UserPrincipal:
    principal: UserPrincipal | None = getattr(request.state, "user_principal", None)
    if principal is None:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return principal


def _to_qdrant_point_id(entity_id: str) -> str:
    """Convert any string ID to a valid Qdrant UUID point ID."""
    try:
        UUID(entity_id)
        return entity_id
    except (ValueError, TypeError):
        return str(uuid5(NAMESPACE_URL, str(entity_id)))


def _parse_java_datetime(value: object) -> str:
    """Convert a Java LocalDateTime value from Qdrant payload to an ISO string.

    Java serialises LocalDateTime as a list [year, month, day, hour, min, sec, nano]
    when using the default Jackson array serialiser.  This helper converts that to
    an ISO-8601 string so downstream code can call ``datetime.fromisoformat()``.

    Strings are returned as-is; anything else falls back to an empty string.
    """
    if isinstance(value, list) and len(value) >= 3:
        parts = (list(value) + [0, 0, 0, 0])[:7]
        year, month, day, hour, minute, sec, nano = [int(p) for p in parts]
        micro = nano // 1000
        return f"{year:04d}-{month:02d}-{day:02d}T{hour:02d}:{minute:02d}:{sec:02d}.{micro:06d}"
    if isinstance(value, str):
        return value
    return ""


async def _build_rrf_feed(user_id: str, flow: PostTypeFlow, n: int) -> list[str]:
    """Shared helper for the two internal RRF endpoints.

    Orchestrates:
    1. Qdrant: retrieve user vector.
    2. Qdrant: ANN search post_vectors → ``user_vector_candidates``.
    3. Qdrant: ANN search user_vectors → ``peer_user_ids``.
    4. ``FeedCandidateService.build_feed`` (handles friends, trending, VIEW exclusion).

    Cold-start fallback (no user vector / no initialInterests):
    - Trending: shows the most popular recent posts.
    - Global Centroid: queries Qdrant with the average platform vector to
      surface "generally appealing" content.
    """
    qdrant = get_qdrant_client()
    settings = get_settings()

    user_point_id = _to_qdrant_point_id(user_id)

    # ── Step 1: Retrieve user vector ──────────────────────────────────────────
    user_vector: list[float] | None = None
    has_initial_interests = False

    try:
        user_points = qdrant.retrieve(
            collection_name=settings.qdrant_user_collection_name,
            ids=[user_point_id],
            with_vectors=[_VECTOR_NAME],
            with_payload=True,
        )
    except Exception:
        logger.exception("[RRF] Qdrant retrieve failed for user_id=%s", user_id)
        user_points = []

    if user_points and user_points[0].vector is not None:
        raw_vec = user_points[0].vector
        if isinstance(raw_vec, dict):
            raw_vec = next(iter(raw_vec.values()))
        user_vector = raw_vec

        payload = user_points[0].payload or {}
        initial_interests = payload.get("initial_interests") or []
        has_initial_interests = len(initial_interests) > 0

    # ── Cold-start check: no vector OR empty initialInterests ─────────────────
    is_cold_start = user_vector is None or not has_initial_interests

    if is_cold_start:
        logger.info(
            "[RRF] Cold-start detected for user_id=%s (vector=%s, interests=%s) — "
            "falling back to trending + global centroid",
            user_id,
            "present" if user_vector is not None else "missing",
            "yes" if has_initial_interests else "no",
        )
        return await _build_cold_start_feed(user_id, flow, n)

    # ── Step 2: ANN search for post candidates (user_vector source) ───────────
    try:
        post_response = qdrant.query_points(
            collection_name=settings.qdrant_collection_name,
            query=user_vector,
            using=_VECTOR_NAME,
            limit=_RECALL_LIMIT,
            with_payload=True,
        )
        post_hits = post_response.points
    except Exception:
        logger.exception("[RRF] Qdrant post search failed for user_id=%s", user_id)
        post_hits = []

    user_vector_candidates: list[dict[str, Any]] = []
    for hit in post_hits:
        payload: dict[str, Any] = hit.payload or {}
        post_id = payload.get("post_id")
        if not post_id:
            continue
        raw_at = payload.get("uploaded_at") or payload.get("created_at")
        user_vector_candidates.append({
            "id":            post_id,
            "created_at":    _parse_java_datetime(raw_at),
            "initial_score": float(hit.score),
            "post_type":     str(payload.get("post_type") or "").upper(),
            "author_id":     payload.get("author_id"),
            "group_id":      payload.get("group_id"),
            "stats":         payload.get("stats") or {},
            "content":       payload.get("content"),
            "media":         payload.get("media") or [],
            "visibility":    payload.get("visibility"),
        })

    # ── Step 3: ANN search for peer user IDs ──────────────────────────────────
    try:
        peer_response = qdrant.query_points(
            collection_name=settings.qdrant_user_collection_name,
            query=user_vector,
            using=_VECTOR_NAME,
            limit=_PEER_LIMIT + 1,  # +1 to exclude self
            with_payload=True,
        )
        peer_hits = peer_response.points
    except Exception:
        logger.exception("[RRF] Qdrant peer search failed for user_id=%s", user_id)
        peer_hits = []

    peer_user_ids: list[str] = []
    for hit in peer_hits:
        peer_id = (hit.payload or {}).get("user_id")
        if peer_id and peer_id != user_id:
            peer_user_ids.append(peer_id)
        if len(peer_user_ids) >= _PEER_LIMIT:
            break

    # ── Step 4: Build feed via FeedCandidateService ───────────────────────────
    engine = RRFRecommendationEngine()
    candidate_service = FeedCandidateService(engine=engine)

    post_ids = await candidate_service.build_feed(
        user_id=user_id,
        user_vector_candidates=user_vector_candidates,
        peer_user_ids=peer_user_ids,
        random_candidates=[],   # random slot filled from other sources fallback
        flow=flow,
        n=n,
    )

    logger.info("[RRF] flow=%s user_id=%s → %d ranked post IDs", flow.value, user_id, len(post_ids))
    return post_ids


async def _build_cold_start_feed(user_id: str, flow: PostTypeFlow, n: int) -> list[str]:
    """Build a feed for cold-start users who have no personalised vector.

    Two complementary strategies are combined:

    1. **Trending / Popularity** — fetch the most-interacted-with posts from
       the social-feed-service.  These are posts with the highest engagement
       in the recent window and need no vector at all.

    2. **Global Centroid Query** — compute the mean vector of all indexed posts
       in Qdrant, then run an ANN search with it.  This returns "generally
       appealing" content that represents the platform's overall taste.

    Both streams are fed into the standard RRF engine so the final list is
    properly deduplicated and ranked.
    """
    qdrant = get_qdrant_client()
    settings = get_settings()

    # ── Strategy 1: Global Centroid ANN search ────────────────────────────────
    centroid_candidates: list[dict[str, Any]] = []
    centroid = compute_global_centroid()

    if centroid is not None:
        try:
            post_response = qdrant.query_points(
                collection_name=settings.qdrant_collection_name,
                query=centroid,
                using=_VECTOR_NAME,
                limit=_RECALL_LIMIT,
                with_payload=True,
            )
            for hit in post_response.points:
                payload: dict[str, Any] = hit.payload or {}
                post_id = payload.get("post_id")
                if not post_id:
                    continue
                raw_at = payload.get("uploaded_at") or payload.get("created_at")
                centroid_candidates.append({
                    "id":            post_id,
                    "created_at":    _parse_java_datetime(raw_at),
                    "initial_score": float(hit.score),
                    "post_type":     str(payload.get("post_type") or "").upper(),
                    "author_id":     payload.get("author_id"),
                    "group_id":      payload.get("group_id"),
                    "stats":         payload.get("stats") or {},
                    "content":       payload.get("content"),
                    "media":         payload.get("media") or [],
                    "visibility":    payload.get("visibility"),
                })
            logger.info(
                "[RRF][ColdStart] Global centroid ANN returned %d candidates for user_id=%s",
                len(centroid_candidates), user_id,
            )
        except Exception:
            logger.exception("[RRF][ColdStart] Global centroid ANN search failed for user_id=%s", user_id)
    else:
        logger.warning("[RRF][ColdStart] Could not compute global centroid — skipping centroid source")

    # ── Strategy 2: Trending is handled by FeedCandidateService automatically ─
    # The trending stream is always fetched inside build_feed(), so we just pass
    # the centroid results as the user_vector source and let RRF merge them with
    # the trending + friend streams.

    engine = RRFRecommendationEngine()
    candidate_service = FeedCandidateService(engine=engine)

    post_ids = await candidate_service.build_feed(
        user_id=user_id,
        user_vector_candidates=centroid_candidates,
        peer_user_ids=[],       # no peers for cold-start users
        random_candidates=[],
        flow=flow,
        n=n,
    )

    logger.info(
        "[RRF][ColdStart] flow=%s user_id=%s → %d ranked post IDs (centroid=%d, trending via build_feed)",
        flow.value, user_id, len(post_ids), len(centroid_candidates),
    )
    return post_ids


# ── Existing authenticated / public endpoints ──────────────────────────────────

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


@router.post(
    "/internal/recommendations/users/{user_id}/seed",
    summary="[Internal] Seed initial interests and rebuild baseline vector",
)
async def seed_user_interests(user_id: str, request: UserInterestSeedUpdateRequest) -> ApiResponse[dict]:
    """
    Called by social-feed-service during data seeding.
    Updates the user's initial interests in Qdrant, reconstructs the baseline
    vector using fastembed, and recalculates the dynamic vector.
    """
    from app.services.user_vectorizer import UserProfile, build_baseline_user_document
    from app.services.dynamic_vector_service import update_user_vector

    qdrant = get_qdrant_client()
    settings = get_settings()
    user_point_id = _to_qdrant_point_id(user_id)

    try:
        points = qdrant.retrieve(
            collection_name=settings.qdrant_user_collection_name,
            ids=[user_point_id],
            with_payload=True,
        )
    except Exception:
        logger.exception("[Seed] Failed to retrieve user %s from Qdrant", user_id)
        return ApiResponse.error("Failed to retrieve user from Qdrant", code="INTERNAL_ERROR")

    if not points:
        payload = {
            "user_id": user_id,
            "initial_interests": request.initial_interests,
        }
    else:
        payload = points[0].payload or {}
        payload["initial_interests"] = request.initial_interests

    profile = UserProfile(
        id=user_id,
        bio=payload.get("bio", ""),
        initialInterests=request.initial_interests,
    )
    semantic_text = build_baseline_user_document(profile)

    try:
        qdrant.set_model(settings.embedding_model_name)
        qdrant.add(
            collection_name=settings.qdrant_user_collection_name,
            documents=[semantic_text],
            metadata=[payload],
            ids=[user_point_id],
        )
        logger.info("[Seed] Re-embedded baseline vector for user_id=%s with new interests", user_id)
    except Exception:
        logger.exception("[Seed] Failed to upsert new baseline vector for user_id=%s", user_id)
        return ApiResponse.error("Failed to upsert baseline vector", code="INTERNAL_ERROR")

    await update_user_vector(user_id)

    return ApiResponse.success({"updated": True, "interests_count": len(request.initial_interests)})


@router.post(
    "/internal/recommendations/users/{user_id}/sync",
    summary="[Internal] Sync user profile from user-service and rebuild baseline",
)
async def sync_user_profile(user_id: str) -> ApiResponse[dict]:
    """
    Fetches the latest profile from user-service, updates Qdrant,
    re-embeds the baseline, and recalculates the dynamic vector.
    """
    from app.clients.user_service_client import get_user_profile
    from app.services.user_vectorizer import UserProfile, build_baseline_user_document
    from app.services.dynamic_vector_service import update_user_vector

    profile_data = await get_user_profile(user_id)
    if not profile_data:
        return ApiResponse.error("Failed to fetch user profile from user-service", code="INTERNAL_ERROR")

    qdrant = get_qdrant_client()
    settings = get_settings()
    user_point_id = _to_qdrant_point_id(user_id)

    # Initial interests defaults to empty list if not present
    initial_interests = profile_data.get("initialInterests") or []
    
    try:
        points = qdrant.retrieve(
            collection_name=settings.qdrant_user_collection_name,
            ids=[user_point_id],
            with_payload=True,
        )
    except Exception:
        logger.exception("[Sync] Failed to retrieve user %s from Qdrant", user_id)
        return ApiResponse.error("Failed to retrieve user from Qdrant", code="INTERNAL_ERROR")

    if not points:
        payload = {
            "user_id": user_id,
            "account_id": profile_data.get("accountId"),
            "full_name": profile_data.get("fullName"),
            "bio": profile_data.get("bio", ""),
            "initial_interests": initial_interests,
        }
    else:
        payload = points[0].payload or {}
        payload["full_name"] = profile_data.get("fullName")
        payload["bio"] = profile_data.get("bio", "")
        payload["initial_interests"] = initial_interests

    profile = UserProfile(
        id=user_id,
        fullName=profile_data.get("fullName", ""),
        bio=payload.get("bio", ""),
        initialInterests=initial_interests,
    )
    semantic_text = build_baseline_user_document(profile)

    try:
        qdrant.set_model(settings.embedding_model_name)
        qdrant.add(
            collection_name=settings.qdrant_user_collection_name,
            documents=[semantic_text],
            metadata=[payload],
            ids=[user_point_id],
        )
        logger.info("[Sync] Re-embedded baseline vector for user_id=%s with latest profile", user_id)
    except Exception:
        logger.exception("[Sync] Failed to upsert new baseline vector for user_id=%s", user_id)
        return ApiResponse.error("Failed to upsert baseline vector", code="INTERNAL_ERROR")

    await update_user_vector(user_id)

    return ApiResponse.success({"updated": True, "interests_count": len(initial_interests)})


@router.post(
    "/internal/recommendations/users/sync-all",
    summary="[Internal] Sync all user profiles from user-service and rebuild vectors",
)
async def sync_all_users() -> ApiResponse[dict]:
    from app.clients.user_service_client import get_users_batch
    from app.services.user_vectorizer import UserProfile, build_baseline_user_document
    from app.services.dynamic_vector_service import update_user_vector
    import asyncio

    qdrant = get_qdrant_client()
    settings = get_settings()
    
    total_synced = 0
    last_id = None
    batch_size = 500
    
    while True:
        users = await get_users_batch(last_id=last_id, size=batch_size)
        if not users:
            break
            
        documents = []
        payloads = []
        point_ids = []
        user_ids = []
        
        user_ids_batch = [u["id"] for u in users if u.get("id")]
        qdrant_ids = [_to_qdrant_point_id(uid) for uid in user_ids_batch]
        
        try:
            existing_points = qdrant.retrieve(
                collection_name=settings.qdrant_user_collection_name,
                ids=qdrant_ids,
                with_payload=True,
            )
            existing_payloads = {str(p.id): (p.payload or {}) for p in existing_points}
        except Exception:
            logger.exception("[SyncAll] Failed to retrieve batch from Qdrant")
            existing_payloads = {}
            
        for u in users:
            uid = u.get("id")
            if not uid:
                continue
                
            qdrant_id = _to_qdrant_point_id(uid)
            initial_interests = u.get("initialInterests") or []
            bio = u.get("bio", "")
            
            payload = existing_payloads.get(qdrant_id, {})
            if not payload:
                payload = {
                    "user_id": uid,
                    "account_id": u.get("accountId"),
                }
            payload["full_name"] = u.get("fullName")
            payload["bio"] = bio
            payload["initial_interests"] = initial_interests
            
            profile = UserProfile(
                id=uid,
                fullName=u.get("fullName", ""),
                bio=bio,
                initialInterests=initial_interests,
            )
            
            documents.append(build_baseline_user_document(profile))
            payloads.append(payload)
            point_ids.append(qdrant_id)
            user_ids.append(uid)
            
        if point_ids:
            try:
                qdrant.set_model(settings.embedding_model_name)
                qdrant.add(
                    collection_name=settings.qdrant_user_collection_name,
                    documents=documents,
                    metadata=payloads,
                    ids=point_ids,
                )
            except Exception:
                logger.exception("[SyncAll] Failed to bulk upsert baseline vectors")
                
            tasks = [update_user_vector(uid) for uid in user_ids]
            await asyncio.gather(*tasks, return_exceptions=True)
            
            total_synced += len(point_ids)
            logger.info("[SyncAll] Synced batch of %d users, total so far: %d", len(point_ids), total_synced)
            
        if len(users) < batch_size:
            break
            
        last_id = users[-1].get("id")

    return ApiResponse.success({"updated": True, "total_synced": total_synced})


# ── New internal RRF endpoints (called by social-feed-service) ─────────────────

@router.get(
    "/internal/rrf/feed/{user_id}",
    summary="[Internal] Get RRF-ranked FEED_SHARE post IDs for a user",
    description=(
        "Returns an ordered list of post ID strings for the FEED_SHARE flow "
        "(FEED + SHARE post types) using the 5-stream RRF pipeline with VIEW-based "
        "exclusion. Called by social-feed-service for feed hydration."
    ),
)
async def get_rrf_feed(
    user_id: str,
    n: int = Query(default=20, ge=1, le=100, description="Number of post IDs to return"),
) -> ApiResponse[list[str]]:
    """
    Internal RRF feed endpoint — FEED_SHARE flow.

    Orchestrates Qdrant recall, peer discovery, friend/trending/VIEW fetching
    (via ``FeedCandidateService``), and returns the ranked post IDs only.
    The caller (social-feed-service) hydrates the IDs into ``PostResponse`` objects.
    """
    post_ids = await _build_rrf_feed(user_id=user_id, flow=PostTypeFlow.FEED_SHARE, n=n)
    return ApiResponse.success(post_ids)


@router.get(
    "/internal/rrf/reels/{user_id}",
    summary="[Internal] Get RRF-ranked REEL post IDs for a user",
    description=(
        "Returns an ordered list of post ID strings for the REEL flow using "
        "the 5-stream RRF pipeline with VIEW-based exclusion. "
        "Called by social-feed-service for reel feed hydration."
    ),
)
async def get_rrf_reels(
    user_id: str,
    n: int = Query(default=20, ge=1, le=100, description="Number of post IDs to return"),
) -> ApiResponse[list[str]]:
    """
    Internal RRF reels endpoint — REEL flow.

    Same orchestration as ``get_rrf_feed`` but with REEL post types and
    REEL-specific decay rates / source weights (trending boosted).
    """
    post_ids = await _build_rrf_feed(user_id=user_id, flow=PostTypeFlow.REEL, n=n)
    return ApiResponse.success(post_ids)


@router.get(
    "/internal/vector-store/stats",
    summary="[Internal] Vector store statistics for all Qdrant collections",
)
async def get_vector_store_stats() -> ApiResponse[dict]:
    """
    Returns key stats for every Qdrant collection:
    - vectors_count  – total indexed vectors
    - indexed_vectors_count – vectors in the HNSW index
    - points_count   – logical points (may differ from vectors for sparse)
    - segments_count
    - status         – green / yellow / grey
    - vector_size
    - distance metric
    """
    qdrant = get_qdrant_client()
    settings = get_settings()

    collection_names = [
        settings.qdrant_collection_name,
        settings.qdrant_user_collection_name,
    ]

    result: dict[str, dict] = {}

    for name in collection_names:
        try:
            info = qdrant.get_collection(name)
            config = info.config.params.vectors
            # config can be a dict (named vectors) or a single VectorParams
            if isinstance(config, dict):
                # named vectors — pick the first one for size/distance
                first = next(iter(config.values()))
                vector_size = first.size
                distance = first.distance.name if first.distance else "UNKNOWN"
            else:
                vector_size = getattr(config, "size", None)
                distance = config.distance.name if getattr(config, "distance", None) else "UNKNOWN"

            result[name] = {
                "status": info.status.name if info.status else "UNKNOWN",
                "vectors_count": info.vectors_count or 0,
                "indexed_vectors_count": info.indexed_vectors_count or 0,
                "points_count": info.points_count or 0,
                "segments_count": info.segments_count or 0,
                "vector_size": vector_size,
                "distance": distance,
            }
        except Exception:
            logger.exception("[Stats] Failed to fetch info for collection %s", name)
            result[name] = {"status": "ERROR", "error": f"Collection '{name}' not found or unreachable"}

    return ApiResponse.success(result)
