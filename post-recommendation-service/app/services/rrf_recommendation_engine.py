"""
RRFRecommendationEngine — Hybrid Recommendation Engine with Dual Post-Type Flows.

Mathematical Overview
---------------------

This engine fuses five candidate streams into a single personalized feed using
a two-phase pipeline.  Crucially, it supports **two independent flows** driven
by the post's content type:

    Flow 1 — FEED / SHARE (``PostTypeFlow.FEED_SHARE``)
        Classic social timeline.  Candidates whose ``post_type`` is ``FEED``
        *or* ``SHARE`` are recommended together.  Relevance is weighted toward
        the user's personal interest vector and friends, with moderate time
        decay — a good post stays relevant for hours to a day.

    Flow 2 — REELS (``PostTypeFlow.REEL``)
        Short-video discovery feed.  Only candidates with ``post_type == REEL``
        are considered.  Trending signal receives a higher weight because reel
        engagement is more viral/ephemeral.  Decay is faster — a reel that
        isn't trending within hours is effectively stale.

Each flow has its own:
  - Accepted post types (for candidate filtering)
  - Source weights (how much each signal contributes to RRF fusion)
  - Per-source decay rates (λ values)

Phase 1 · Time-Decayed Ranking
    For every source except "random", each candidate receives a *decayed score*:

        S_decayed = S_initial · exp(−λ · Δt_hours)

    where Δt is the age of the post in hours, and λ is a per-flow, per-source
    decay rate.  Each source list is then sorted descending by S_decayed to
    establish its *internal rank*.

Phase 2 · Weighted Reciprocal Rank Fusion (RRF)
    The first four source lists are merged via Weighted RRF:

        Score(d) = Σ_{source ∈ {1,2,3,4}}  W_source / (k + rank_source(d))

    The k constant (default 60) controls the rank-bias vs relevance trade-off
    (see docstring on ``weighted_rrf_fusion``).

Phase 3 · Random Injection
    Random posts (flow-filtered) are injected by slotting:
    ⌊N × 0.85⌋ RRF items + ⌈N × 0.15⌉ random items, interleaved.

Flow weight comparison
    Source                FEED_SHARE   REEL
    --------------------  ----------   ----
    User Vector Similarity   0.30      0.25
    Peer User Posts          0.20      0.15
    Friend Posts             0.20      0.15
    Trending Posts           0.15      0.30
    Random Posts             0.15      0.15  (injected, not fused)
"""

from __future__ import annotations

import logging
import math
import random
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any

logger = logging.getLogger(__name__)

# ── Source keys ────────────────────────────────────────────────────────────────

SOURCE_USER_VECTOR: str = "user_vector"
SOURCE_PEER_POSTS: str = "peer_posts"
SOURCE_FRIEND_POSTS: str = "friend_posts"
SOURCE_TRENDING: str = "trending_posts"
SOURCE_RANDOM: str = "random_posts"

# Ordered list of the four RRF-fused sources (random is excluded from fusion)
_RRF_SOURCES: list[str] = [
    SOURCE_USER_VECTOR,
    SOURCE_PEER_POSTS,
    SOURCE_FRIEND_POSTS,
    SOURCE_TRENDING,
]

# ── RRF constant ───────────────────────────────────────────────────────────────

_DEFAULT_K: float = 60.0

# ── Random injection ratio ─────────────────────────────────────────────────────

_RRF_RATIO: float = 0.85   # fraction of feed slots filled by RRF winners
_RANDOM_RATIO: float = 0.15  # fraction filled by random injection


# ── Post-type flow ─────────────────────────────────────────────────────────────

class PostTypeFlow(str, Enum):
    """Declares which content type a recommendation request targets.

    Attributes:
        FEED_SHARE: Classic social timeline — includes ``FEED`` and ``SHARE``
            post types.  Optimized for relevance-heavy, moderately time-decayed
            ranking where friends and personal interests dominate.
        REEL: Short-video discovery feed — includes only the ``REEL`` post
            type.  Optimized for viral/trending signals with aggressive time
            decay because reel engagement is highly ephemeral.
    """

    FEED_SHARE = "feed_share"
    REEL = "reel"


# Post types accepted per flow (values match the ``post_type`` field on posts)
_FLOW_ACCEPTED_TYPES: dict[PostTypeFlow, frozenset[str]] = {
    PostTypeFlow.FEED_SHARE: frozenset({"FEED", "SHARE"}),
    PostTypeFlow.REEL:       frozenset({"REEL"}),
}

# ── Per-flow source weights ────────────────────────────────────────────────────
#
# FEED_SHARE: friends + personal vector dominate; trending is secondary.
# REEL:       trending is elevated (viral discovery); personal vector and
#             friends are de-weighted because reels live on exploration.

_FLOW_SOURCE_WEIGHTS: dict[PostTypeFlow, dict[str, float]] = {
    PostTypeFlow.FEED_SHARE: {
        SOURCE_USER_VECTOR:  0.30,
        SOURCE_PEER_POSTS:   0.20,
        SOURCE_FRIEND_POSTS: 0.20,
        SOURCE_TRENDING:     0.15,
        SOURCE_RANDOM:       0.15,
    },
    PostTypeFlow.REEL: {
        SOURCE_USER_VECTOR:  0.25,
        SOURCE_PEER_POSTS:   0.15,
        SOURCE_FRIEND_POSTS: 0.15,
        SOURCE_TRENDING:     0.30,   # boosted — reels spread virally
        SOURCE_RANDOM:       0.15,
    },
}

# ── Per-flow, per-source decay rates (λ) ──────────────────────────────────────
#
# FEED_SHARE: moderate decay — a well-crafted post is relevant for a day or two.
# REEL:       fast decay — a reel's viral window is measured in hours, not days.
#
# Half-life approximation:  t½ ≈ ln(2) / λ  (in hours)
#   λ=0.02 → t½ ≈ 34 h    λ=0.05 → t½ ≈ 14 h    λ=0.20 → t½ ≈ 3.5 h

_FLOW_DECAY_RATES: dict[PostTypeFlow, dict[str, float]] = {
    PostTypeFlow.FEED_SHARE: {
        SOURCE_USER_VECTOR:  0.02,   # ~34 h half-life
        SOURCE_PEER_POSTS:   0.03,   # ~23 h
        SOURCE_FRIEND_POSTS: 0.05,   # ~14 h
        SOURCE_TRENDING:     0.10,   # ~7 h
    },
    PostTypeFlow.REEL: {
        SOURCE_USER_VECTOR:  0.05,   # ~14 h — reels exhaust interest faster
        SOURCE_PEER_POSTS:   0.08,   # ~9 h
        SOURCE_FRIEND_POSTS: 0.10,   # ~7 h
        SOURCE_TRENDING:     0.20,   # ~3.5 h — viral reels peak and die fast
    },
}


# ── Post data model ────────────────────────────────────────────────────────────

@dataclass
class PostCandidate:
    """Lightweight representation of an input post candidate.

    Attributes:
        id: Unique identifier for the post.
        created_at: UTC timestamp of post creation.  Used to compute Δt.
        initial_score: The raw relevance signal from the source (e.g., cosine
            similarity for user_vector, view count for trending).
        post_type: String label matching the source system's type field
            (e.g., ``"FEED"``, ``"SHARE"``, ``"REEL"``).  Used for flow
            filtering in ``generate_source_ranks``.
        decayed_score: Populated by ``calculate_exponential_decay``; initial 0.0.
        extra: Any additional payload fields (passthrough to output).
    """

    id: str
    created_at: datetime
    initial_score: float
    post_type: str = ""
    decayed_score: float = field(default=0.0, init=False)
    extra: dict[str, Any] = field(default_factory=dict)


def _parse_post(raw: dict[str, Any]) -> PostCandidate:
    """Construct a ``PostCandidate`` from a raw dictionary.

    Accepts both offset-aware and offset-naive ``created_at`` datetimes.
    If ``created_at`` is a string it is parsed with ``datetime.fromisoformat``.

    Args:
        raw: Dict with keys ``id``, ``created_at``, and ``initial_score``.
            The optional key ``post_type`` is read if present.
            Any additional keys are stored in ``extra``.

    Returns:
        A hydrated ``PostCandidate``.

    Raises:
        KeyError: If required keys are missing from *raw*.
        ValueError: If ``created_at`` cannot be parsed.
    """
    created_at = raw["created_at"]
    if isinstance(created_at, str):
        created_at = datetime.fromisoformat(created_at)
    elif isinstance(created_at, list):
        # Java LocalDateTime serialised as array: [year, month, day, hour, min, sec, nano]
        # Pad to 7 elements; convert nanoseconds -> microseconds.
        parts = (list(created_at) + [0, 0, 0, 0])[:7]
        year, month, day, hour, minute, sec, nano = [int(p) for p in parts]
        created_at = datetime(year, month, day, hour, minute, sec,
                              nano // 1000, tzinfo=timezone.utc)

    # Normalise to UTC-aware
    if created_at.tzinfo is None:
        created_at = created_at.replace(tzinfo=timezone.utc)

    known_keys = {"id", "created_at", "initial_score", "post_type"}
    extra = {k: v for k, v in raw.items() if k not in known_keys}

    return PostCandidate(
        id=raw["id"],
        created_at=created_at,
        initial_score=float(raw["initial_score"]),
        post_type=str(raw.get("post_type", "")).upper(),
        extra=extra,
    )


# ── Engine ─────────────────────────────────────────────────────────────────────

class RRFRecommendationEngine:
    """Hybrid recommendation engine using Weighted RRF and Exponential Time Decay.

    Supports two independent post-type flows:

    * ``PostTypeFlow.FEED_SHARE`` — timeline posts (FEED, SHARE).
    * ``PostTypeFlow.REEL``       — short-video discovery (REEL).

    Each flow applies its own source weights and per-source decay rates so that
    the ranking signal mix is appropriate for the content category.

    Usage
    -----
    >>> engine = RRFRecommendationEngine(k=60)
    >>> feed  = engine.get_final_feed(streams, n=20, flow=PostTypeFlow.FEED_SHARE)
    >>> reels = engine.get_final_feed(streams, n=20, flow=PostTypeFlow.REEL)

    The engine is *stateless*: it holds only configuration constants and can be
    called concurrently without any shared mutable state.

    Args:
        k: The RRF smoothing constant (see ``weighted_rrf_fusion`` for details).
            Defaults to 60 (the standard Cormack et al. value).
        reference_time: The "now" instant used for Δt computation.  Defaults to
            ``datetime.now(timezone.utc)``.  Inject a fixed value in tests.
    """

    def __init__(
        self,
        k: float = _DEFAULT_K,
        reference_time: datetime | None = None,
    ) -> None:
        self._k = k
        self._reference_time: datetime = reference_time or datetime.now(timezone.utc)

    # ── Public methods ─────────────────────────────────────────────────────────

    def calculate_exponential_decay(
        self,
        post: PostCandidate,
        lam: float,
    ) -> float:
        """Apply exponential time decay to a post's initial score.

        Formula::

            S_decayed = S_initial · exp(−λ · Δt_hours)

        The exponential decay model reflects the empirical observation that
        content relevance diminishes roughly exponentially over time.  A higher
        λ makes content "go stale" faster.

        Args:
            post: The ``PostCandidate`` whose score is being decayed.
            lam: The decay rate (λ), in units of 1/hour.  Larger values mean
                faster decay.  Typical range: [0.01, 0.20].

        Returns:
            The non-negative decayed score as a float.

        Notes:
            - Δt is clipped to 0 so that posts with a ``created_at`` slightly in
              the future (clock skew) are not penalised.
            - The result is written back into ``post.decayed_score`` for
              convenience.
        """
        delta_hours = max(
            0.0,
            (self._reference_time - post.created_at).total_seconds() / 3600.0,
        )
        decayed = post.initial_score * math.exp(-lam * delta_hours)
        post.decayed_score = decayed
        return decayed

    def generate_source_ranks(
        self,
        sources: dict[str, list[dict[str, Any]]],
        flow: PostTypeFlow = PostTypeFlow.FEED_SHARE,
        exclude_ids: set[str] | None = None,
    ) -> dict[str, list[PostCandidate]]:
        """Parse raw source lists, filter by flow post types, apply decay, and rank.

        For each source:

        1. **Parse** raw dicts into ``PostCandidate`` objects.
        2. **Filter** — discard candidates whose ``post_type`` is not in the
           accepted set for *flow*.  Posts with a blank/missing ``post_type``
           are *kept* to preserve backwards compatibility with older events.
        3. **Decay** (RRF sources only) — apply ``exp(−λ·Δt)`` using the
           flow-appropriate λ for this source.
        4. **Sort** (RRF sources only) — sort descending by ``decayed_score``
           to establish the internal rank consumed by RRF fusion.

        The *random* source is parsed and filtered but returned **unsorted**
        because its items have no meaningful rank.

        Args:
            sources: Mapping of source name → list of raw post dicts.
                Each dict must contain ``id``, ``created_at``, and
                ``initial_score``.  The ``post_type`` key is optional but
                recommended for correct flow filtering.
            flow: Target post-type flow.  Determines accepted post types,
                decay rates, and source weights used downstream.
                Defaults to ``PostTypeFlow.FEED_SHARE``.

        Returns:
            Mapping of source name → flow-filtered, sorted list of
            ``PostCandidate`` objects.
        """
        accepted_types = _FLOW_ACCEPTED_TYPES[flow]
        decay_rates = _FLOW_DECAY_RATES[flow]
        ranked: dict[str, list[PostCandidate]] = {}
        _exclude: set[str] = exclude_ids or set()

        for source_name, raw_posts in sources.items():
            candidates: list[PostCandidate] = []
            for raw in raw_posts:
                # Early-out: skip posts already viewed by the user (O(1) set lookup,
                # runs before the more expensive _parse_post call).
                if raw.get("id") in _exclude:
                    continue

                try:
                    post = _parse_post(raw)
                except (KeyError, ValueError) as exc:
                    logger.warning(
                        "Skipping malformed post in source=%s: %s", source_name, exc
                    )
                    continue

                # Flow filter: keep if type matches, or if type is unknown (blank)
                if post.post_type and post.post_type not in accepted_types:
                    continue

                candidates.append(post)

            if source_name == SOURCE_RANDOM:
                # Random posts: filtered but unsorted; no decay applied
                ranked[source_name] = candidates
                logger.debug(
                    "flow=%s source=%s: %d random candidates",
                    flow.value, source_name, len(candidates),
                )
                continue

            # Apply flow-specific decay with the per-source λ
            lam = decay_rates.get(source_name, decay_rates.get(SOURCE_USER_VECTOR, 0.02))
            for post in candidates:
                self.calculate_exponential_decay(post, lam)

            # Sort descending by decayed score → establishes internal rank
            candidates.sort(key=lambda p: p.decayed_score, reverse=True)
            ranked[source_name] = candidates

            logger.debug(
                "flow=%s source=%s: %d candidates after decay (λ=%.3f)",
                flow.value, source_name, len(candidates), lam,
            )

        return ranked

    def weighted_rrf_fusion(
        self,
        ranked_sources: dict[str, list[PostCandidate]],
        flow: PostTypeFlow = PostTypeFlow.FEED_SHARE,
    ) -> list[tuple[str, float]]:
        """Fuse ranked source lists into a single score per post via Weighted RRF.

        Formula
        -------
        For each document *d* present in any of the four RRF sources::

            Score(d) = Σ_{source} W_source · 1 / (k + rank_source(d))

        where ``rank_source(d)`` is the **1-based** position of *d* in that
        source's ranked list (rank 1 = best candidate in that source).
        ``W_source`` is drawn from the *flow-specific* weight table.

        The RRF k Constant — Rank-Bias vs Relevance Trade-off
        -------------------------------------------------------
        The constant *k* dampens the contribution of high-rank positions:

        * **k = 0**: Collapses to 1/rank.  Rank 1 dominates (1.0 vs 0.5 for
          rank 2).  The system is **rank-biased** — a single strong signal wins.
        * **k = 60 (default)**: Rank 1 → 1/61 ≈ 0.0164; rank 2 → 1/62 ≈ 0.0161.
          The marginal value of ranking first in one source is tiny, so a document
          must appear high in *multiple* sources to accumulate a meaningful score.
          The system is **relevance-centric** and robust to noisy individual sources.
        * **k → ∞**: All ranks contribute equally — degenerates to vote count.

        Cormack et al. (2009) empirically found k=60 optimal for metasearch, and
        it has become the de-facto standard.  Increase k to trust consensus;
        decrease k toward 0 to let the top source dominate.

        Documents *absent* from a source are assigned rank = len(source) + 1
        (worse than last place) — a light penalty for incomplete coverage.

        Args:
            ranked_sources: Output of ``generate_source_ranks``.  Only keys in
                ``_RRF_SOURCES`` are consumed; the random source is ignored.
            flow: Determines which weight table is used.
                Defaults to ``PostTypeFlow.FEED_SHARE``.

        Returns:
            List of ``(post_id, rrf_score)`` tuples sorted descending (best first).
        """
        weights = _FLOW_SOURCE_WEIGHTS[flow]

        # Collect all unique post IDs across RRF-eligible sources
        all_post_ids: set[str] = set()
        for source_name in _RRF_SOURCES:
            for post in ranked_sources.get(source_name, []):
                all_post_ids.add(post.id)

        if not all_post_ids:
            return []

        # Build fast rank look-ups: source → {post_id → 1-based rank}
        rank_maps: dict[str, dict[str, int]] = {}
        source_lengths: dict[str, int] = {}
        for source_name in _RRF_SOURCES:
            posts = ranked_sources.get(source_name, [])
            source_lengths[source_name] = len(posts)
            rank_maps[source_name] = {post.id: (i + 1) for i, post in enumerate(posts)}

        # Compute Weighted RRF score for every candidate
        scores: dict[str, float] = {}
        k = self._k

        for post_id in all_post_ids:
            score = 0.0
            for source_name in _RRF_SOURCES:
                rank = rank_maps[source_name].get(post_id, source_lengths[source_name] + 1)
                score += weights[source_name] * (1.0 / (k + rank))
            scores[post_id] = score

        sorted_results = sorted(scores.items(), key=lambda x: x[1], reverse=True)

        logger.info(
            "flow=%s RRF fusion: %d unique candidates from %d sources",
            flow.value, len(sorted_results), len(_RRF_SOURCES),
        )
        return sorted_results

    def get_final_feed(
        self,
        sources: dict[str, list[dict[str, Any]]],
        n: int = 20,
        flow: PostTypeFlow = PostTypeFlow.FEED_SHARE,
        shuffle_random: bool = True,
        random_seed: int | None = None,
        exclude_ids: set[str] | None = None,
    ) -> list[str]:
        """Build the final ranked feed of *n* post IDs for the given post-type flow.

        Pipeline
        --------
        1. Parse, flow-filter, exclude already-viewed posts, and rank each
           source via ``generate_source_ranks``.
        2. Fuse the four ranked sources via ``weighted_rrf_fusion`` using
           flow-specific weights.
        3. Slot random posts:

           - ``n_rrf  = ⌊n × 0.85⌋`` items from the RRF result.
           - ``n_rand = n − n_rrf`` items sampled from the (flow-filtered) random pool.
           - If the pool is smaller than n_rand, all available random posts are used.

        4. Interleave random posts uniformly across the RRF list.

        Flow selection
        --------------
        Pass ``flow=PostTypeFlow.FEED_SHARE`` for timeline recommendations
        (only ``FEED``/``SHARE`` posts) or ``flow=PostTypeFlow.REEL`` for the
        short-video discovery feed (only ``REEL`` posts).  Posts whose
        ``post_type`` does not match the flow are silently excluded at source-
        ranking time, so callers can safely pass unsegregated candidate pools.

        Args:
            sources: Mapping of source name → list of raw post dicts.
                Expected keys: ``user_vector``, ``peer_posts``,
                ``friend_posts``, ``trending_posts``, ``random_posts``.
                Missing sources are treated as empty lists.
            n: Total number of posts to return.  Must be ≥ 1.
            flow: Target post-type flow.  Determines accepted post types,
                decay rates, and source weights.
                Defaults to ``PostTypeFlow.FEED_SHARE``.
            shuffle_random: If *True* (default), the random pool is shuffled
                before sampling.  Set *False* in tests for determinism.
            random_seed: Optional seed for ``random.Random``.
            exclude_ids: Set of post IDs to exclude from the feed (e.g. posts
                the user has already viewed).  Forwarded to
                ``generate_source_ranks`` for early-out filtering before any
                parsing or decay computation.

        Returns:
            Ordered list of at most *n* post ID strings (best-ranked first).
            Random-injected posts are interleaved uniformly.

        Raises:
            ValueError: If *n* < 1.
        """
        if n < 1:
            raise ValueError(f"n must be at least 1, got {n}")

        rng = random.Random(random_seed)

        # ── Step 1: Rank sources (flow filter + view exclusion + time decay) ──
        ranked_sources = self.generate_source_ranks(
            sources, flow=flow, exclude_ids=exclude_ids
        )

        # ── Step 2: Weighted RRF fusion (first four sources) ──────────────────
        fused: list[tuple[str, float]] = self.weighted_rrf_fusion(ranked_sources, flow=flow)

        # ── Step 3: Determine slot sizes ──────────────────────────────────────
        n_rrf = math.floor(n * _RRF_RATIO)   # ⌊n × 0.85⌋
        n_rand = n - n_rrf                    # ⌈n × 0.15⌉

        top_rrf = fused[:n_rrf]

        random_pool = ranked_sources.get(SOURCE_RANDOM, [])
        if shuffle_random:
            rng.shuffle(random_pool)
        random_sample = random_pool[:n_rand]

        logger.info(
            "flow=%s feed: %d RRF + %d random (target n=%d)",
            flow.value, len(top_rrf), len(random_sample), n,
        )

        # ── Step 4: Interleave random posts uniformly into the RRF list ───────
        final_ids: list[str] = [pid for pid, _score in top_rrf]
        if random_sample:
            step = max(1, len(final_ids) // (len(random_sample) + 1))
            for i, rand_post in enumerate(random_sample):
                insert_idx = min((i + 1) * step + i, len(final_ids))
                final_ids.insert(insert_idx, rand_post.id)

        # ── Step 5: Deduplicate, preserving rank order ─────────────────────────
        seen: set[str] = set()
        result: list[str] = []
        for post_id in final_ids:
            if post_id not in seen:
                seen.add(post_id)
                result.append(post_id)

        return result
