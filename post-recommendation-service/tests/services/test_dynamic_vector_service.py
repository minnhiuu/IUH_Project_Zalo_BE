"""
Unit tests for dynamic_vector_service.

All external dependencies (Qdrant, social_feed_client) are mocked — no
live infrastructure is needed to run these tests.
"""

import math
import pytest
import numpy as np
from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock, MagicMock, patch

# ── helpers ────────────────────────────────────────────────────────────────

def _make_qdrant_point(point_id: str, vector: list[float], payload: dict | None = None):
    p = MagicMock()
    p.id = point_id
    p.vector = vector
    p.payload = payload or {}
    return p


def _iso(days_ago: float) -> str:
    return (datetime.now(UTC) - timedelta(days=days_ago)).isoformat()


BASELINE_VECTOR = [1.0, 0.0, 0.0]  # unit vector along x
POST_VECTOR_A   = [0.0, 1.0, 0.0]  # unit vector along y
POST_VECTOR_B   = [0.0, 0.0, 1.0]  # unit vector along z

USER_ID  = "00000000-0000-0000-0000-000000000001"
POST_ID_A = "00000000-0000-0000-0000-000000000010"
POST_ID_B = "00000000-0000-0000-0000-000000000011"

INTERACTIONS_ONE = [
    {"postId": POST_ID_A, "createdAt": _iso(0), "weight": 1.0},
]

INTERACTIONS_TWO = [
    {"postId": POST_ID_A, "createdAt": _iso(0), "weight": 1.0},
    {"postId": POST_ID_B, "createdAt": _iso(14), "weight": 1.0},
]


# ── tests ──────────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_alpha_blend_applied_and_output_normalised():
    """Happy path: V_base + V_int → alpha-blended, L2-normalised vector."""
    from app.services import dynamic_vector_service as svc

    with (
        patch("app.services.dynamic_vector_service.get_qdrant_client") as mock_qdrant_factory,
        patch("app.services.dynamic_vector_service.social_feed_client.get_newest_interactions", new_callable=AsyncMock) as mock_interactions,
        patch("app.services.dynamic_vector_service.get_settings") as mock_settings_factory,
    ):
        settings = MagicMock()
        settings.qdrant_user_collection_name = "user_vectors"
        settings.qdrant_collection_name = "post_vectors"
        settings.user_vector_alpha = 0.7
        settings.user_vector_interaction_limit = 150
        settings.user_vector_decay_half_life_days = 7.0
        mock_settings_factory.return_value = settings

        qdrant = MagicMock()
        # Baseline user point
        qdrant.retrieve.side_effect = [
            [_make_qdrant_point(USER_ID, BASELINE_VECTOR)],  # user retrieve
            [_make_qdrant_point(POST_ID_A, POST_VECTOR_A)],  # post retrieve
        ]
        qdrant.get_collection.return_value.config.params.vectors = MagicMock(spec=dict)
        qdrant.get_collection.return_value.config.params.vectors.__class__ = dict
        # Make isinstance(vectors_config, dict) = False so we get the unnamed path
        type(qdrant.get_collection.return_value.config.params).vectors = property(
            lambda self: [1.0] * 3  # not a dict → unnamed vector path
        )
        mock_qdrant_factory.return_value = qdrant

        mock_interactions.return_value = INTERACTIONS_ONE

        result = await svc.update_user_vector(USER_ID)

    assert result is True
    qdrant.upsert.assert_called_once()
    upserted_point = qdrant.upsert.call_args.kwargs["points"][0]
    v_out = np.array(upserted_point.vector)
    # Must be unit length (L2-normalised)
    assert abs(np.linalg.norm(v_out) - 1.0) < 1e-5


@pytest.mark.asyncio
async def test_fallback_to_baseline_when_no_interactions():
    """If social-feed returns empty list, we do NOT upsert and return False."""
    from app.services import dynamic_vector_service as svc

    with (
        patch("app.services.dynamic_vector_service.get_qdrant_client") as mock_qdrant_factory,
        patch("app.services.dynamic_vector_service.social_feed_client.get_newest_interactions", new_callable=AsyncMock) as mock_interactions,
        patch("app.services.dynamic_vector_service.get_settings") as mock_settings_factory,
    ):
        settings = MagicMock()
        settings.qdrant_user_collection_name = "user_vectors"
        settings.qdrant_collection_name = "post_vectors"
        settings.user_vector_alpha = 0.7
        settings.user_vector_interaction_limit = 150
        settings.user_vector_decay_half_life_days = 7.0
        mock_settings_factory.return_value = settings

        qdrant = MagicMock()
        qdrant.retrieve.return_value = [_make_qdrant_point(USER_ID, BASELINE_VECTOR)]
        mock_qdrant_factory.return_value = qdrant

        mock_interactions.return_value = []

        result = await svc.update_user_vector(USER_ID)

    assert result is False
    qdrant.upsert.assert_not_called()


@pytest.mark.asyncio
async def test_fallback_when_no_post_vectors_in_qdrant():
    """Interactions returned, but none resolve to a Qdrant post vector → False."""
    from app.services import dynamic_vector_service as svc

    with (
        patch("app.services.dynamic_vector_service.get_qdrant_client") as mock_qdrant_factory,
        patch("app.services.dynamic_vector_service.social_feed_client.get_newest_interactions", new_callable=AsyncMock) as mock_interactions,
        patch("app.services.dynamic_vector_service.get_settings") as mock_settings_factory,
    ):
        settings = MagicMock()
        settings.qdrant_user_collection_name = "user_vectors"
        settings.qdrant_collection_name = "post_vectors"
        settings.user_vector_alpha = 0.7
        settings.user_vector_interaction_limit = 150
        settings.user_vector_decay_half_life_days = 7.0
        mock_settings_factory.return_value = settings

        qdrant = MagicMock()
        qdrant.retrieve.side_effect = [
            [_make_qdrant_point(USER_ID, BASELINE_VECTOR)],  # user retrieve
            [],  # post retrieve — nothing found
        ]
        mock_qdrant_factory.return_value = qdrant
        mock_interactions.return_value = INTERACTIONS_ONE

        result = await svc.update_user_vector(USER_ID)

    assert result is False
    qdrant.upsert.assert_not_called()


def test_time_decay_older_gets_lower_weight():
    """Older interactions must receive smaller decay weights."""
    from app.services.dynamic_vector_service import _decay_weight

    half_life = 7.0
    w_fresh  = _decay_weight(_iso(0),  half_life)
    w_week   = _decay_weight(_iso(7),  half_life)
    w_month  = _decay_weight(_iso(30), half_life)

    assert w_fresh > w_week > w_month
    # After exactly one half-life, weight should be ≈ 0.5
    assert abs(w_week - 0.5) < 0.01


def test_l2_normalization_gives_unit_vector():
    """_l2_normalize must always return a vector with ‖v‖ = 1."""
    from app.services.dynamic_vector_service import _l2_normalize

    for raw in ([3.0, 4.0, 0.0], [0.1, 0.2, 0.3], [100.0, 0.0, 0.0]):
        v = _l2_normalize(np.array(raw))
        assert abs(np.linalg.norm(v) - 1.0) < 1e-6


def test_l2_normalization_zero_vector_safe():
    """Zero vector should pass through without NaN/division error."""
    from app.services.dynamic_vector_service import _l2_normalize

    v = _l2_normalize(np.zeros(3))
    assert not np.any(np.isnan(v))
