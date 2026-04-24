"""
RecommenderEngine — 4-stage personalized feed pipeline.

Mathematical overview (for thesis documentation)
-------------------------------------------------

Stage 1 · Recall
    Query Qdrant's 'post_vectors' collection with the user's current dynamic
    vector V_user (alpha-blended baseline + interaction centroid).  Returns
    the top-K semantic candidates ranked by cosine similarity.

Stage 2 · Filtering
    Remove candidates whose post_id appears in:
    - The user's seen/hidden post set (MongoDB ``seen_posts`` collection).
    - Posts authored by the user themselves.

Stage 3 · Social Signal
    Identify the user's "Interest Peers": the M users whose Qdrant vectors are
    closest to V_user (excluding the user themselves).  Any candidate post that
    appears in at least one peer's recent interactions receives a social-signal
    boost.

Stage 4 · Ranking with Reciprocal Rank Fusion (RRF)
    Three ranked lists are fused with RRF to produce a final ordering:

    1. Semantic list   — ordered by Qdrant cosine-similarity score.
    2. Social list     — ordered by peer-interaction count (social signal).
    3. Popularity list — ordered by a popularity proxy:
                         pop = log(1 + views) + 0.5·log(1 + reactions)

    RRF formula:  RRF(d) = Σ_k  w_k / (rank_k(d) + K_rrf)
    with weights  w_semantic=0.6, w_social=0.3, w_popularity=0.1
    and K_rrf=60 (standard smoothing constant).

    Using RRF instead of a raw linear blend avoids score-scale incompatibility
    and gives each signal equal "positional" influence weighted by its coefficient.
"""

import asyncio
import logging
import math
import time
from uuid import NAMESPACE_URL, UUID, uuid5

from pymongo.database import Database
from qdrant_client import QdrantClient

from app.clients import social_feed_client
from app.repositories import recommendation_repository
from app.schemas.recommendation import FeedItem, FeedResponse

from app.services.dynamic_vector_service import compute_global_centroid

logger = logging.getLogger(__name__)

# ── Constants ──────────────────────────────────────────────────────────────

_RECALL_LIMIT: int = 100          # Stage 1: candidates fetched from Qdrant
_PEER_LIMIT: int = 20             # Stage 3: number of interest peers
_RRF_K: float = 60.0             # RRF smoothing constant (standard value)
_VECTOR_NAME: str = "fast-bge-base-en-v1.5"  # Named vector in Qdrant collections

_W_SEMANTIC: float = 0.6         # Stage 4 weight — semantic cosine similarity
_W_SOCIAL: float = 0.3           # Stage 4 weight — social peer signal
_W_POPULARITY: float = 0.1       # Stage 4 weight — post popularity proxy


# ── Helpers ────────────────────────────────────────────────────────────────

def _to_qdrant_point_id(entity_id: str) -> str:
    """Convert any string ID to a valid Qdrant UUID point ID."""
    try:
        UUID(entity_id)
        return entity_id
    except (ValueError, TypeError):
        return str(uuid5(NAMESPACE_URL, str(entity_id)))


def _popularity_score(stats: dict) -> float:
    """
    Compute a log-scaled popularity proxy from post statistics.

    Formula:  pop = log(1 + views) + 0.5·log(1 + reactions)

    The log transform prevents highly viral posts from completely drowning
    out semantically relevant but less popular ones.

    Args:
        stats: The 'stats' payload dict stored alongside the post vector in Qdrant.

    Returns:
        A non-negative float.
    """
    views = max(0, int(stats.get("view_count") or 0))
    reactions = max(0, int(stats.get("reaction_count") or 0))
    return math.log1p(views) + 0.5 * math.log1p(reactions)


def _rrf_score(rank: int, weight: float) -> float:
    """
    Reciprocal Rank Fusion contribution for a single ranked list.

    Args:
        rank: The 0-based rank in this list.
        weight: The list's contribution weight (must sum to 1.0 across all lists).

    Returns:
        A callable via partial, but used directly as a per-post computation.

    Notes:
        RRF(d) for a single list = weight / (rank(d) + K_rrf)
        Posts absent from a list receive rank = len(list) + 1 (last place).
    """
    return weight / (rank + _RRF_K)


def _build_rrf_scores(
    semantic_ids: list[str],
    social_ids: list[str],
    popularity_ids: list[str],
) -> dict[str, float]:
    """
    Fuse three ranked lists with Reciprocal Rank Fusion into a single score
    per post_id.

    Absent posts are assigned rank = len(list) + 1 (worse than last place).

    Args:
        semantic_ids: Post IDs ordered best→worst by Qdrant cosine score.
        social_ids: Post IDs ordered by social-peer interaction count.
        popularity_ids: Post IDs ordered by popularity proxy (log-scaled).

    Returns:
        dict mapping post_id → final RRF score (higher is better).
    """
    all_ids = set(semantic_ids) | set(social_ids) | set(popularity_ids)

    sem_rank = {pid: i for i, pid in enumerate(semantic_ids)}
    soc_rank = {pid: i for i, pid in enumerate(social_ids)}
    pop_rank = {pid: i for i, pid in enumerate(popularity_ids)}

    n_sem = len(semantic_ids)
    n_soc = len(social_ids)
    n_pop = len(popularity_ids)

    scores: dict[str, float] = {}
    for pid in all_ids:
        r_sem = sem_rank.get(pid, n_sem + 1)
        r_soc = soc_rank.get(pid, n_soc + 1)
        r_pop = pop_rank.get(pid, n_pop + 1)

        scores[pid] = (
            _W_SEMANTIC   * _rrf_score(r_sem, 1.0)
            + _W_SOCIAL     * _rrf_score(r_soc, 1.0)
            + _W_POPULARITY * _rrf_score(r_pop, 1.0)
        )

    return scores


# ── Engine ─────────────────────────────────────────────────────────────────

class RecommenderEngine:
    """
    Personalized feed recommender using a 4-stage pipeline.

    Stages
    ------
    1. Recall       — Qdrant ANN search with the user's dynamic vector.
    2. Filtering    — Remove seen, hidden, and self-authored posts.
    3. Social       — Identify Interest Peers and their recently liked posts.
    4. Ranking      — Reciprocal Rank Fusion of semantic + social + popularity.

    Args:
        qdrant: A ``QdrantClient`` instance (shared, thread-safe).
        db: A pymongo ``Database`` for the recommendation service.
        settings: The application ``Settings`` object.
    """

    def __init__(self, qdrant: QdrantClient, db: Database, settings) -> None:
        self._qdrant = qdrant
        self._db = db
        self._settings = settings

    # ── Public API ────────────────────────────────────────────────────────

    async def get_personalized_feed(
        self,
        user_id: str,
        page: int = 0,
        page_size: int = 20,
    ) -> FeedResponse:
        """
        Build a personalized ranked feed for *user_id*.

        Args:
            user_id: The requesting user's ID.
            page: Zero-based page index for pagination.
            page_size: Number of items to return.

        Returns:
            A ``FeedResponse`` with ranked ``FeedItem`` objects.

        Raises:
            ValueError: If *user_id* is blank.
            RuntimeError: If the user has no vector in Qdrant (not yet indexed).
        """
        if not user_id or not user_id.strip():
            raise ValueError("user_id must not be blank")

        user_point_id = _to_qdrant_point_id(user_id)
        t_start = time.perf_counter()
        logger.info("[Pipeline] START user_id=%s page=%d page_size=%d", user_id, page, page_size)

        # ── Stage 1: Recall ───────────────────────────────────────────────
        t1 = time.perf_counter()
        candidates, user_vector = await self._stage1_recall(user_point_id, user_id)
        logger.info("[Pipeline] Stage1 done in %.0fms — %d candidates",
                    (time.perf_counter() - t1) * 1000, len(candidates))

        if not candidates:
            logger.warning("[Pipeline] Stage1 returned 0 candidates — returning empty feed for user_id=%s", user_id)
            return FeedResponse(
                user_id=user_id,
                items=[],
                total=0,
                page=page,
                page_size=page_size,
            )

        # ── Stage 2: Filtering ────────────────────────────────────────────
        t2 = time.perf_counter()
        candidates = await self._stage2_filter(candidates, user_id)
        logger.info("[Pipeline] Stage2 done in %.0fms — %d candidates remaining",
                    (time.perf_counter() - t2) * 1000, len(candidates))

        if not candidates:
            logger.warning("[Pipeline] Stage2 filtered all candidates — returning empty feed for user_id=%s", user_id)
            return FeedResponse(
                user_id=user_id,
                items=[],
                total=0,
                page=page,
                page_size=page_size,
            )

        # ── Stage 3: Social Signal ────────────────────────────────────────
        t3 = time.perf_counter()
        social_post_counts = await self._stage3_social_signal(user_vector, user_id)
        logger.info("[Pipeline] Stage3 done in %.0fms — %d posts with social signal",
                    (time.perf_counter() - t3) * 1000, len(social_post_counts))

        # ── Stage 4: Ranking with RRF ─────────────────────────────────────
        t4 = time.perf_counter()
        ranked = self._stage4_rank(candidates, social_post_counts)
        logger.info("[Pipeline] Stage4 done in %.0fms — %d ranked items",
                    (time.perf_counter() - t4) * 1000, len(ranked))

        # Paginate
        start = page * page_size
        end = start + page_size
        page_items = ranked[start:end]

        logger.info(
            "[Pipeline] DONE in %.0fms — returning %d/%d items (page=%d) for user_id=%s",
            (time.perf_counter() - t_start) * 1000,
            len(page_items), len(ranked), page, user_id,
        )

        return FeedResponse(
            user_id=user_id,
            items=page_items,
            total=len(ranked),
            page=page,
            page_size=page_size,
        )

    # ── Stage implementations ──────────────────────────────────────────────

    async def _stage1_recall(
        self, user_point_id: str, user_id: str
    ) -> tuple[list[dict], list[float]]:
        """
        Stage 1 — Recall via Qdrant ANN Search.

        Retrieves the user's current dynamic vector from the ``user_vectors``
        collection and performs an approximate nearest-neighbour search over
        ``post_vectors`` to get the top-_RECALL_LIMIT semantic candidates.

        Cold-start fallback: when the user has no vector, or has both no
        initialInterests and no interactions with the system, the Global
        Centroid (mean of all post vectors) is used as the query vector so
        the user still receives "generally appealing" content.

        Args:
            user_point_id: Qdrant UUID for the user point.
            user_id: Human-readable user ID (for logging).

        Returns:
            A tuple of (candidates_list, query_vector).
            Each candidate dict has keys: post_id, semantic_score, payload.

        Raises:
            RuntimeError: If Qdrant is completely unreachable.
        """
        try:
            user_points = self._qdrant.retrieve(
                collection_name=self._settings.qdrant_user_collection_name,
                ids=[user_point_id],
                with_vectors=[_VECTOR_NAME],
                with_payload=True,
            )
        except Exception as exc:
            logger.exception("Qdrant retrieve failed for user_id=%s", user_id)
            raise RuntimeError(f"Qdrant unavailable: {exc}") from exc

        # Determine if user has a personalised vector with interests or interactions
        user_vector: list[float] | None = None
        has_initial_interests = False
        has_interactions = False

        if user_points and user_points[0].vector is not None:
            raw_vec = user_points[0].vector
            if isinstance(raw_vec, dict):
                raw_vec = next(iter(raw_vec.values()))
            user_vector = raw_vec

            payload = user_points[0].payload or {}
            initial_interests = payload.get("initial_interests") or []
            has_initial_interests = len(initial_interests) > 0
            has_interactions = (payload.get("vector_version") or 0) > 0

        is_cold_start = user_vector is None or (not has_initial_interests and not has_interactions)

        if is_cold_start:
            logger.info(
                "[Pipeline] Cold-start for user_id=%s (vector=%s, interests=%s, interactions=%s) — "
                "falling back to global centroid",
                user_id,
                "present" if user_vector is not None else "missing",
                "yes" if has_initial_interests else "no",
                "yes" if has_interactions else "no",
            )
            centroid = compute_global_centroid()
            if centroid is None:
                logger.warning(
                    "[Pipeline] Global centroid unavailable — returning empty for user_id=%s",
                    user_id,
                )
                return [], []
            # Use the centroid as the query vector
            user_vector = centroid

        try:
            response = self._qdrant.query_points(
                collection_name=self._settings.qdrant_collection_name,
                query=user_vector,
                using=_VECTOR_NAME,
                limit=_RECALL_LIMIT,
                with_payload=True,
            )
            results = response.points
        except Exception as exc:
            logger.exception("Qdrant search failed for user_id=%s", user_id)
            raise RuntimeError(f"Qdrant search unavailable: {exc}") from exc

        candidates: list[dict] = []
        for hit in results:
            payload = hit.payload or {}
            post_id = payload.get("post_id")
            if not post_id:
                continue
            candidates.append(
                {
                    "post_id": post_id,
                    "semantic_score": float(hit.score),
                    "payload": payload,
                }
            )

        if candidates:
            top = candidates[0]
            logger.info(
                "Stage1 recall: %d candidates for user_id=%s "
                "(top post_id=%s score=%.4f)",
                len(candidates), user_id, top["post_id"], top["semantic_score"],
            )
        else:
            logger.warning("Stage1 recall: 0 candidates for user_id=%s", user_id)
        return candidates, user_vector

    async def _stage2_filter(
        self, candidates: list[dict], user_id: str
    ) -> list[dict]:
        """
        Stage 2 — Filter out seen, hidden, and self-authored posts.

        Exclusion criteria:
        - Post authored by the requesting user (``author_id == user_id``).
        - Post already seen/hidden within the last 30 days (MongoDB lookup).

        Args:
            candidates: List of candidate dicts from Stage 1.
            user_id: The requesting user's ID.

        Returns:
            Filtered list of candidate dicts.
        """
        seen_ids = await recommendation_repository.get_seen_post_ids(
            self._db, user_id
        )

        before = len(candidates)
        filtered = [
            c
            for c in candidates
            if c["post_id"] not in seen_ids
            and c["payload"].get("author_id") != user_id
        ]
        removed_seen = sum(1 for c in candidates if c["post_id"] in seen_ids)
        removed_self = sum(1 for c in candidates
                           if c["payload"].get("author_id") == user_id
                           and c["post_id"] not in seen_ids)

        logger.info(
            "Stage2 filter: %d → %d candidates "
            "(seen/hidden=%d, self-authored=%d) for user_id=%s",
            before, len(filtered), removed_seen, removed_self, user_id,
        )
        return filtered

    async def _stage3_social_signal(
        self, user_vector: list[float], user_id: str
    ) -> dict[str, int]:
        """
        Stage 3 — Social Signal via Interest Peers.

        Identifies the top-_PEER_LIMIT users whose Qdrant vectors are most
        cosine-similar to V_user ("Interest Peers") and fetches the post IDs
        they have recently interacted with.  Any candidate post that appears in
        a peer's interaction history receives a social count boost.

        Mathematical justification:
            Users with similar interest vectors are likely to validate content
            that is both semantically relevant and community-approved.  This
            acts as a collaborative-filtering surrogate without requiring an
            explicit ratings matrix.

        Args:
            user_vector: The user's current dynamic float vector.
            user_id: The requesting user's ID (excluded from peer search).

        Returns:
            dict mapping post_id → number of peers who interacted with it.
            Empty dict on any error (graceful degradation).
        """
        # Find interest peers in Qdrant user collection
        try:
            response = self._qdrant.query_points(
                collection_name=self._settings.qdrant_user_collection_name,
                query=user_vector,
                using=_VECTOR_NAME,
                limit=_PEER_LIMIT + 1,  # +1 to exclude self
                with_payload=True,
            )
            peer_results = response.points
        except Exception:
            logger.exception(
                "Qdrant peer search failed for user_id=%s — skipping social stage",
                user_id,
            )
            return {}

        peer_ids: list[str] = []
        for hit in peer_results:
            peer_user_id = (hit.payload or {}).get("user_id")
            if peer_user_id and peer_user_id != user_id:
                peer_ids.append(peer_user_id)
            if len(peer_ids) >= _PEER_LIMIT:
                break

        if not peer_ids:
            return {}

        # Fetch recent interactions for all peers concurrently
        peer_interaction_tasks = [
            social_feed_client.get_newest_interactions(
                peer_id, self._settings.user_vector_interaction_limit
            )
            for peer_id in peer_ids
        ]
        peer_results_list: list[list[dict]] = await asyncio.gather(
            *peer_interaction_tasks, return_exceptions=True
        )

        # Count how many peers interacted with each post
        post_peer_count: dict[str, int] = {}
        for result in peer_results_list:
            if isinstance(result, Exception):
                continue
            seen_in_this_peer: set[str] = set()
            for ia in result:
                pid = ia.get("postId")
                if pid and pid not in seen_in_this_peer:
                    post_peer_count[pid] = post_peer_count.get(pid, 0) + 1
                    seen_in_this_peer.add(pid)

        logger.info(
            "Stage3 social: %d peers found, %d posts with social signal for user_id=%s",
            len(peer_ids), len(post_peer_count), user_id,
        )
        if post_peer_count:
            top_social = sorted(post_peer_count.items(), key=lambda x: x[1], reverse=True)[:3]
            logger.debug("Stage3 social top posts: %s", top_social)
        return post_peer_count

    def _stage4_rank(
        self,
        candidates: list[dict],
        social_post_counts: dict[str, int],
    ) -> list[FeedItem]:
        """
        Stage 4 — Reciprocal Rank Fusion ranking.

        Constructs three independent ranked lists and fuses them with RRF:

        Semantic list:   candidates ordered by Qdrant cosine-similarity score.
        Social list:     candidates ordered by peer-interaction count (desc).
        Popularity list: candidates ordered by log-scaled popularity proxy (desc).

        Final RRF score per document:
            score(d) = Σ_k  w_k / (rank_k(d) + K_rrf)

        where K_rrf = 60 is the standard smoothing constant that prevents
        documents with very high individual-list ranks from dominating.

        Args:
            candidates: Filtered candidate list from Stage 2.
            social_post_counts: Peer-interaction counts from Stage 3.

        Returns:
            List of ``FeedItem`` objects sorted by descending RRF score.
        """
        # Build per-candidate auxiliary scores
        enriched: list[dict] = []
        for c in candidates:
            post_id = c["post_id"]
            payload = c["payload"]
            social = float(social_post_counts.get(post_id, 0))
            pop = _popularity_score(payload.get("stats") or {})
            enriched.append(
                {
                    **c,
                    "social_score": social,
                    "popularity_score": pop,
                }
            )

        # Build the three ranked lists (best-first)
        semantic_ids = [
            e["post_id"]
            for e in sorted(enriched, key=lambda x: x["semantic_score"], reverse=True)
        ]
        social_ids = [
            e["post_id"]
            for e in sorted(enriched, key=lambda x: x["social_score"], reverse=True)
        ]
        popularity_ids = [
            e["post_id"]
            for e in sorted(
                enriched, key=lambda x: x["popularity_score"], reverse=True
            )
        ]

        # Compute RRF scores
        rrf_scores = _build_rrf_scores(semantic_ids, social_ids, popularity_ids)

        # Sort candidates by final RRF score
        enriched.sort(key=lambda x: rrf_scores.get(x["post_id"], 0.0), reverse=True)

        # Convert to FeedItem
        feed_items: list[FeedItem] = []
        for e in enriched:
            payload = e["payload"]
            feed_items.append(
                FeedItem(
                    post_id=e["post_id"],
                    author_id=payload.get("author_id"),
                    group_id=payload.get("group_id"),
                    post_type=payload.get("post_type"),
                    caption=payload.get("caption"),
                    hashtags=payload.get("hashtags") or [],
                    visibility=payload.get("visibility"),
                    location_name=payload.get("location_name"),
                    media_types=payload.get("media_types") or [],
                    music_title=payload.get("music_title"),
                    music_artist=payload.get("music_artist"),
                    stats=payload.get("stats") or {},
                    semantic_score=e["semantic_score"],
                    social_score=e["social_score"],
                    popularity_score=e["popularity_score"],
                    final_score=rrf_scores.get(e["post_id"], 0.0),
                )
            )

        logger.info(
            "Stage4 RRF ranking: %d items ranked",
            len(feed_items),
        )
        if feed_items:
            top3 = feed_items[:3]
            for rank, item in enumerate(top3, 1):
                logger.debug(
                    "Stage4 top-%d: post_id=%s rrf=%.5f sem=%.4f soc=%.1f pop=%.4f",
                    rank, item.post_id, item.final_score,
                    item.semantic_score, item.social_score, item.popularity_score,
                )

        return feed_items
