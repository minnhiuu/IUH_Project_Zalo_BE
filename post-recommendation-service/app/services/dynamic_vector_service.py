"""
Dynamic User Vector Service
============================
Implements the alpha-blended user vector update:

    V_user = (1 - α) · V_base  +  α · V_int

V_base  — stable baseline already in Qdrant (written on registration).
V_int   — time-decayed centroid of the N most-recent interaction post vectors.
α       — configurable blend coefficient (default 0.7, "Behavior-First").

The final vector is L2-normalised before being upserted back to Qdrant so that
cosine similarity queries work correctly.
"""

import logging
import math
from datetime import UTC, datetime
from uuid import NAMESPACE_URL, UUID, uuid5

import numpy as np
from qdrant_client.http.models import PointStruct, VectorParams

from app.clients import social_feed_client
from app.clients.qdrant_client import get_qdrant_client
from app.core.config import get_settings

logger = logging.getLogger(__name__)

_FALLBACK_VECTOR_VERSION = 0


def _to_qdrant_point_id(entity_id: str) -> str:
    try:
        UUID(entity_id)
        return entity_id
    except (ValueError, TypeError):
        return str(uuid5(NAMESPACE_URL, str(entity_id)))


def _decay_weight(created_at_iso: str | None, half_life_days: float) -> float:
    """Exponential time-decay: w = exp(-λ·Δdays), λ = ln(2) / half_life_days."""
    if not created_at_iso:
        return 1.0
    try:
        created_at = datetime.fromisoformat(created_at_iso.replace("Z", "+00:00"))
        delta_days = (datetime.now(UTC) - created_at).total_seconds() / 86_400.0
        lam = math.log(2) / half_life_days
        return math.exp(-lam * delta_days)
    except Exception:
        return 1.0


def _l2_normalize(vec: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(vec)
    if norm < 1e-12:
        return vec
    return vec / norm


async def update_user_vector(user_id: str) -> bool:
    """
    Compute and upsert the dynamic user vector for *user_id*.

    Returns True if the vector was updated, False if we fell back to the
    baseline or encountered an unrecoverable error.
    """
    settings = get_settings()
    qdrant = get_qdrant_client()
    user_point_id = _to_qdrant_point_id(user_id)
    collection = settings.qdrant_user_collection_name

    # ── 1. Fetch baseline from Qdrant ──────────────────────────────────────
    try:
        points = qdrant.retrieve(
            collection_name=collection,
            ids=[user_point_id],
            with_vectors=True,
            with_payload=True,
        )
    except Exception:
        logger.exception("Failed to retrieve baseline vector for user_id=%s", user_id)
        return False

    if not points or points[0].vector is None:
        logger.warning("No baseline vector found in Qdrant for user_id=%s — skipping", user_id)
        return False

    raw_vector = points[0].vector
    # qdrant_client.add() stores vectors under a named key when using fastembed
    if isinstance(raw_vector, dict):
        # grab the first (and only) named vector
        raw_vector = next(iter(raw_vector.values()))
    v_base = np.array(raw_vector, dtype=np.float32)
    existing_payload: dict = points[0].payload or {}

    # ── 2. Fetch recent interactions ───────────────────────────────────────
    interactions: list[dict] = await social_feed_client.get_newest_interactions(
        user_id=user_id,
        limit=settings.user_vector_interaction_limit,
    )

    if not interactions:
        logger.info("No interactions for user_id=%s — baseline vector kept unchanged", user_id)
        return False

    # ── 3. Resolve post vectors and compute decayed centroid (V_int) ───────
    post_collection = settings.qdrant_collection_name
    weighted_sum = np.zeros_like(v_base)
    total_weight = 0.0

    # Batch-retrieve all post vectors in one round-trip
    post_ids = [
        _to_qdrant_point_id(ia["postId"])
        for ia in interactions
        if ia.get("postId")
    ]

    if not post_ids:
        logger.info("Interactions for user_id=%s carry no postId — falling back", user_id)
        return False

    try:
        post_points = qdrant.retrieve(
            collection_name=post_collection,
            ids=post_ids,
            with_vectors=True,
        )
    except Exception:
        logger.exception("Failed to retrieve post vectors from Qdrant for user_id=%s", user_id)
        return False

    # Build a quick lookup: qdrant_point_id → numpy vector
    post_vector_map: dict[str, np.ndarray] = {}
    for pp in post_points:
        if pp.vector is None:
            continue
        raw = pp.vector
        if isinstance(raw, dict):
            raw = next(iter(raw.values()))
        post_vector_map[str(pp.id)] = np.array(raw, dtype=np.float32)

    # Map each interaction back to its post vector using the same ID transform
    for ia in interactions:
        post_id = ia.get("postId")
        if not post_id:
            continue
        point_id = _to_qdrant_point_id(post_id)
        post_vec = post_vector_map.get(point_id)
        if post_vec is None:
            continue  # post not yet indexed — skip gracefully

        weight = _decay_weight(ia.get("createdAt"), settings.user_vector_decay_half_life_days)
        # Multiply by interaction weight if present (defaults to 1.0)
        interaction_weight = float(ia.get("weight") or 1.0)
        w = weight * interaction_weight
        weighted_sum += w * post_vec
        total_weight += w

    if total_weight < 1e-12:
        logger.info(
            "No resolvable post vectors for user_id=%s interactions — baseline kept", user_id
        )
        return False

    v_int = weighted_sum / total_weight

    # ── 4. Alpha-blend and normalise ───────────────────────────────────────
    alpha = settings.user_vector_alpha
    v_dynamic = (1.0 - alpha) * v_base + alpha * v_int
    v_dynamic = _l2_normalize(v_dynamic)

    # ── 5. Upsert back to Qdrant ──────────────────────────────────────────
    vector_version = int(existing_payload.get("vector_version") or _FALLBACK_VECTOR_VERSION) + 1

    updated_payload = {
        **existing_payload,
        "updated_at": datetime.now(UTC).isoformat(),
        "vector_version": vector_version,
        "alpha": alpha,
        "interaction_count_used": len([ia for ia in interactions if ia.get("postId")]),
    }

    # We use the low-level upsert() because v_dynamic is already a raw float
    # array, not a document to be embedded by fastembed.
    # The vector name must match the named vector config used by the collection.
    # If the collection uses the fastembed default name we keep it unnamed.
    try:
        collection_info = qdrant.get_collection(collection_name=collection)
        vectors_config = collection_info.config.params.vectors

        if isinstance(vectors_config, dict):
            # Named vectors — pick the first key (fastembed default is "")
            vector_name = next(iter(vectors_config))
            vector_payload: dict | list = {vector_name: v_dynamic.tolist()}
        else:
            # Single unnamed vector
            vector_payload = v_dynamic.tolist()

        qdrant.upsert(
            collection_name=collection,
            points=[
                PointStruct(
                    id=user_point_id,
                    vector=vector_payload,
                    payload=updated_payload,
                )
            ],
        )
    except Exception:
        logger.exception("Failed to upsert dynamic vector for user_id=%s", user_id)
        return False

    logger.info(
        "Updated dynamic vector for user_id=%s | version=%d | alpha=%.2f | interactions=%d",
        user_id,
        vector_version,
        alpha,
        updated_payload["interaction_count_used"],
    )
    return True
