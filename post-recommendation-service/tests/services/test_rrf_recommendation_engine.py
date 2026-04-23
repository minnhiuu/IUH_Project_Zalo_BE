"""
Unit tests for app.services.rrf_recommendation_engine (dual-flow edition).

Test groups:
- TestCalculateExponentialDecay  — decay formula, edge cases
- TestGenerateSourceRanks        — sorting, flow filtering, random passthrough
- TestWeightedRrfFusion          — score formula, per-flow weights, absent penalty
- TestGetFinalFeed               — slot sizes, deduplication, interleaving, flow isolation
"""

from __future__ import annotations

import math
from datetime import datetime, timedelta, timezone
from typing import Any

import pytest

from app.services.rrf_recommendation_engine import (
    SOURCE_FRIEND_POSTS,
    SOURCE_PEER_POSTS,
    SOURCE_RANDOM,
    SOURCE_TRENDING,
    SOURCE_USER_VECTOR,
    PostCandidate,
    PostTypeFlow,
    RRFRecommendationEngine,
    _DEFAULT_K,
    _FLOW_SOURCE_WEIGHTS,
)

# ── Fixtures ───────────────────────────────────────────────────────────────────

NOW = datetime(2025, 6, 1, 12, 0, 0, tzinfo=timezone.utc)
ONE_HOUR_AGO = NOW - timedelta(hours=1)
TEN_HOURS_AGO = NOW - timedelta(hours=10)


def _make_engine(k: float = _DEFAULT_K) -> RRFRecommendationEngine:
    return RRFRecommendationEngine(k=k, reference_time=NOW)


def _make_raw(
    post_id: str,
    created_at: datetime = ONE_HOUR_AGO,
    initial_score: float = 1.0,
    post_type: str = "FEED",
    **extra: Any,
) -> dict[str, Any]:
    return {
        "id": post_id,
        "created_at": created_at.isoformat(),
        "initial_score": initial_score,
        "post_type": post_type,
        **extra,
    }


# ── Tests: calculate_exponential_decay ────────────────────────────────────────

class TestCalculateExponentialDecay:
    def _candidate(self, created_at: datetime, initial_score: float) -> PostCandidate:
        return PostCandidate(id="p", created_at=created_at, initial_score=initial_score)

    def test_decay_reduces_score(self) -> None:
        engine = _make_engine()
        post = self._candidate(ONE_HOUR_AGO, 1.0)
        result = engine.calculate_exponential_decay(post, lam=0.1)
        assert result == pytest.approx(math.exp(-0.1), rel=1e-9)

    def test_zero_age_returns_initial_score(self) -> None:
        engine = _make_engine()
        post = self._candidate(NOW, 0.75)
        assert engine.calculate_exponential_decay(post, lam=0.5) == pytest.approx(0.75)

    def test_negative_delta_clipped(self) -> None:
        engine = _make_engine()
        post = self._candidate(NOW + timedelta(hours=5), 1.0)
        assert engine.calculate_exponential_decay(post, lam=0.1) == pytest.approx(1.0)

    def test_writes_back_to_post(self) -> None:
        engine = _make_engine()
        post = self._candidate(TEN_HOURS_AGO, 2.0)
        result = engine.calculate_exponential_decay(post, lam=0.05)
        assert post.decayed_score == pytest.approx(result)

    def test_higher_lambda_decays_faster(self) -> None:
        engine = _make_engine()
        slow = engine.calculate_exponential_decay(self._candidate(TEN_HOURS_AGO, 1.0), lam=0.01)
        fast = engine.calculate_exponential_decay(self._candidate(TEN_HOURS_AGO, 1.0), lam=0.20)
        assert slow > fast


# ── Tests: generate_source_ranks ──────────────────────────────────────────────

class TestGenerateSourceRanks:
    def test_rrf_source_sorted_descending_by_decayed_score(self) -> None:
        engine = _make_engine()
        sources = {
            SOURCE_USER_VECTOR: [
                _make_raw("p1", ONE_HOUR_AGO, 0.5, "FEED"),
                _make_raw("p2", TEN_HOURS_AGO, 1.0, "FEED"),
                _make_raw("p3", ONE_HOUR_AGO, 0.9, "FEED"),
            ]
        }
        ranked = engine.generate_source_ranks(sources, flow=PostTypeFlow.FEED_SHARE)
        scores = [p.decayed_score for p in ranked[SOURCE_USER_VECTOR]]
        assert scores == sorted(scores, reverse=True)

    def test_flow_filter_excludes_wrong_type_in_feed_share(self) -> None:
        """REEL posts must be stripped from a FEED_SHARE flow request."""
        engine = _make_engine()
        sources = {
            SOURCE_USER_VECTOR: [
                _make_raw("feed1", post_type="FEED"),
                _make_raw("reel1", post_type="REEL"),
                _make_raw("share1", post_type="SHARE"),
            ]
        }
        ranked = engine.generate_source_ranks(sources, flow=PostTypeFlow.FEED_SHARE)
        ids = {p.id for p in ranked[SOURCE_USER_VECTOR]}
        assert "feed1" in ids
        assert "share1" in ids
        assert "reel1" not in ids

    def test_flow_filter_excludes_wrong_type_in_reel(self) -> None:
        """FEED and SHARE posts must be stripped from a REEL flow request."""
        engine = _make_engine()
        sources = {
            SOURCE_TRENDING: [
                _make_raw("r1", post_type="REEL"),
                _make_raw("f1", post_type="FEED"),
                _make_raw("s1", post_type="SHARE"),
            ]
        }
        ranked = engine.generate_source_ranks(sources, flow=PostTypeFlow.REEL)
        ids = {p.id for p in ranked[SOURCE_TRENDING]}
        assert "r1" in ids
        assert "f1" not in ids
        assert "s1" not in ids

    def test_blank_post_type_passes_filter(self) -> None:
        """Posts without a post_type field are not discarded (backwards compat)."""
        engine = _make_engine()
        sources = {SOURCE_PEER_POSTS: [{"id": "x", "created_at": ONE_HOUR_AGO.isoformat(), "initial_score": 1.0}]}
        ranked = engine.generate_source_ranks(sources, flow=PostTypeFlow.REEL)
        assert any(p.id == "x" for p in ranked[SOURCE_PEER_POSTS])

    def test_random_source_not_sorted_and_no_decay(self) -> None:
        engine = _make_engine()
        sources = {SOURCE_RANDOM: [_make_raw("r1", post_type="REEL"), _make_raw("r2", post_type="REEL")]}
        ranked = engine.generate_source_ranks(sources, flow=PostTypeFlow.REEL)
        for post in ranked[SOURCE_RANDOM]:
            assert post.decayed_score == 0.0

    def test_malformed_post_skipped(self) -> None:
        engine = _make_engine()
        sources = {
            SOURCE_TRENDING: [
                _make_raw("good", post_type="REEL"),
                {"missing": True},
            ]
        }
        ranked = engine.generate_source_ranks(sources, flow=PostTypeFlow.REEL)
        assert len(ranked[SOURCE_TRENDING]) == 1

    def test_empty_source_returns_empty_list(self) -> None:
        engine = _make_engine()
        ranked = engine.generate_source_ranks({SOURCE_PEER_POSTS: []})
        assert ranked[SOURCE_PEER_POSTS] == []


# ── Tests: weighted_rrf_fusion ────────────────────────────────────────────────

class TestWeightedRrfFusion:
    def _build(self, ids: list[str], post_type: str = "FEED") -> list[PostCandidate]:
        return [PostCandidate(id=pid, created_at=ONE_HOUR_AGO, initial_score=1.0, post_type=post_type) for pid in ids]

    def test_sorted_descending(self) -> None:
        engine = _make_engine()
        ranked_sources = {
            SOURCE_USER_VECTOR: self._build(["p1", "p2", "p3"]),
            SOURCE_PEER_POSTS:  self._build(["p1", "p3", "p2"]),
            SOURCE_FRIEND_POSTS: self._build(["p1"]),
            SOURCE_TRENDING:    self._build(["p2", "p1"]),
        }
        results = engine.weighted_rrf_fusion(ranked_sources, flow=PostTypeFlow.FEED_SHARE)
        scores = [s for _, s in results]
        assert scores == sorted(scores, reverse=True)

    def test_cross_source_consensus_wins(self) -> None:
        engine = _make_engine()
        ranked_sources = {
            SOURCE_USER_VECTOR:  self._build(["multi", "solo"]),
            SOURCE_PEER_POSTS:   self._build(["multi"]),
            SOURCE_FRIEND_POSTS: self._build(["multi"]),
            SOURCE_TRENDING:     self._build(["multi"]),
        }
        results = dict(engine.weighted_rrf_fusion(ranked_sources, flow=PostTypeFlow.FEED_SHARE))
        assert results["multi"] > results["solo"]

    def test_reel_flow_uses_reel_weights(self) -> None:
        """Trending weight is higher in REEL flow.

        We verify this directly: when other sources have *different* posts
        (so absent rank > 1 for 'trend_only'), the REEL run scores
        'trend_only' more highly than the FEED_SHARE run because
        W_trend(REEL)=0.30 > W_trend(FEED_SHARE)=0.15.
        """
        engine = _make_engine(k=60)

        # Put decoy posts in the other three sources so that absent rank > 1
        # for "trend_only", making W_trend the *only* differentiator.
        decoys = self._build(["d1", "d2", "d3"])
        ranked_sources = {
            SOURCE_USER_VECTOR:  decoys,
            SOURCE_PEER_POSTS:   decoys,
            SOURCE_FRIEND_POSTS: decoys,
            SOURCE_TRENDING:     self._build(["trend_only"], post_type="REEL"),
        }

        feed_score = dict(engine.weighted_rrf_fusion(ranked_sources, flow=PostTypeFlow.FEED_SHARE)).get("trend_only", 0.0)
        reel_score = dict(engine.weighted_rrf_fusion(ranked_sources, flow=PostTypeFlow.REEL)).get("trend_only", 0.0)

        w_feed = _FLOW_SOURCE_WEIGHTS[PostTypeFlow.FEED_SHARE][SOURCE_TRENDING]
        w_reel = _FLOW_SOURCE_WEIGHTS[PostTypeFlow.REEL][SOURCE_TRENDING]

        # Confirm config is correct
        assert w_reel > w_feed

        # With identical absent ranks in uv/peer/friend, the only score delta
        # comes from W_trend × 1/(k+1), so REEL must outscore FEED_SHARE.
        assert reel_score > feed_score

    def test_empty_sources_returns_empty(self) -> None:
        engine = _make_engine()
        assert engine.weighted_rrf_fusion({}) == []

    def test_absent_post_penalised(self) -> None:
        engine = _make_engine(k=60)
        ranked_sources = {
            SOURCE_USER_VECTOR:  self._build(["everywhere", "uv_only"]),
            SOURCE_PEER_POSTS:   self._build(["everywhere"]),
            SOURCE_FRIEND_POSTS: self._build(["everywhere"]),
            SOURCE_TRENDING:     self._build(["everywhere"]),
        }
        results = dict(engine.weighted_rrf_fusion(ranked_sources, flow=PostTypeFlow.FEED_SHARE))
        assert results["everywhere"] > results["uv_only"]


# ── Tests: get_final_feed ─────────────────────────────────────────────────────

def _build_sources(
    n_rrf: int = 10,
    n_random: int = 5,
    feed_share_type: str = "FEED",
    reel_type: str = "REEL",
) -> dict[str, list[dict[str, Any]]]:
    """Mixed-type sources to verify flow isolation."""
    base = [_make_raw(f"v{i}", ONE_HOUR_AGO, float(i) / 10, feed_share_type) for i in range(n_rrf)]
    reels = [_make_raw(f"rl{i}", ONE_HOUR_AGO, float(i) / 10, reel_type) for i in range(n_rrf)]
    mixed = base + reels  # interleave both types into every source

    return {
        SOURCE_USER_VECTOR:  list(mixed),
        SOURCE_PEER_POSTS:   list(mixed),
        SOURCE_FRIEND_POSTS: list(mixed),
        SOURCE_TRENDING:     list(mixed),
        SOURCE_RANDOM: [
            _make_raw(f"rf{i}", ONE_HOUR_AGO, 0.5, feed_share_type) for i in range(n_random)
        ] + [
            _make_raw(f"rr{i}", ONE_HOUR_AGO, 0.5, reel_type) for i in range(n_random)
        ],
    }


class TestGetFinalFeed:
    """Tests for the refactored get_final_feed that now returns list[str] post IDs."""

    def test_returns_list_of_strings(self) -> None:
        engine = _make_engine()
        result = engine.get_final_feed(_build_sources(), n=10)
        assert isinstance(result, list)
        assert all(isinstance(item, str) for item in result)

    def test_feed_share_flow_returns_only_feed_share_ids(self) -> None:
        """All IDs in FEED_SHARE output must have come from FEED/SHARE-typed posts."""
        engine = _make_engine()
        sources = _build_sources()
        # Collect which IDs are FEED/SHARE in the source
        feed_share_ids = {
            raw["id"]
            for src in sources.values()
            for raw in src
            if raw.get("post_type", "") in ("FEED", "SHARE", "")
        }
        result = engine.get_final_feed(sources, n=20, flow=PostTypeFlow.FEED_SHARE)
        for post_id in result:
            assert post_id in feed_share_ids

    def test_reel_flow_returns_only_reel_ids(self) -> None:
        """All IDs in REEL output must have come from REEL-typed posts."""
        engine = _make_engine()
        sources = _build_sources()
        reel_ids = {
            raw["id"]
            for src in sources.values()
            for raw in src
            if raw.get("post_type", "") in ("REEL", "")
        }
        result = engine.get_final_feed(sources, n=20, flow=PostTypeFlow.REEL)
        for post_id in result:
            assert post_id in reel_ids

    def test_returns_at_most_n_items(self) -> None:
        engine = _make_engine()
        result = engine.get_final_feed(_build_sources(n_rrf=20, n_random=10), n=15)
        assert len(result) <= 15

    def test_no_duplicate_post_ids(self) -> None:
        engine = _make_engine()
        result = engine.get_final_feed(_build_sources(), n=20)
        assert len(result) == len(set(result))

    def test_raises_on_n_less_than_1(self) -> None:
        with pytest.raises(ValueError, match="n must be at least 1"):
            _make_engine().get_final_feed({}, n=0)

    def test_handles_empty_sources_gracefully(self) -> None:
        assert _make_engine().get_final_feed({}, n=10) == []

    def test_deterministic_with_seed(self) -> None:
        engine = _make_engine()
        sources = _build_sources(n_rrf=20, n_random=10)
        ids1 = engine.get_final_feed(sources, n=15, random_seed=42)
        ids2 = engine.get_final_feed(sources, n=15, random_seed=42)
        assert ids1 == ids2

    def test_insufficient_random_pool(self) -> None:
        """With only 1 random-type post, the feed should still be <= n items."""
        engine = _make_engine()
        sources = _build_sources(n_rrf=20, n_random=1)
        result = engine.get_final_feed(sources, n=20, flow=PostTypeFlow.FEED_SHARE)
        assert len(result) <= 20

    def test_feed_and_reel_flows_are_disjoint(self) -> None:
        """For posts with unambiguous types, FEED_SHARE and REEL outputs should not overlap."""
        engine = _make_engine()
        sources = _build_sources(n_rrf=10, n_random=5)
        feed_ids = set(engine.get_final_feed(sources, n=20, flow=PostTypeFlow.FEED_SHARE))
        reel_ids = set(engine.get_final_feed(sources, n=20, flow=PostTypeFlow.REEL))
        # No ID should appear in both flows (each post type only belongs to one flow)
        overlap = feed_ids & reel_ids
        assert not overlap, f"Flows share post IDs: {overlap}"
