# Post Recommendation Service Deep Dive

This document explains how the post-recommendation-service works in depth:

- architecture and module structure,
- request and event flows,
- ranking logic,
- vector update logic,
- feature map and extension points.

It is written for backend developers who need to maintain or extend the service.

---

## 1. Service Purpose and Responsibilities

The post-recommendation-service is a Python FastAPI service that computes personalized post recommendations.

It is responsible for:

- indexing post and user representations as vectors in Qdrant,
- maintaining dynamic user vectors from behavior signals,
- generating ranked recommendations through hybrid ranking,
- exposing internal APIs consumed by social-feed-service.

It is not responsible for:

- hydration of full post DTOs for client responses (social-feed-service handles hydration),
- authoring or mutating social content (social-feed-service owns posts/comments/reactions),
- moderation and notification delivery (social-feed-service + notification-service own those concerns).

---

## 2. High-Level Architecture

Dependencies:

- Qdrant: vector storage and ANN retrieval.
- MongoDB: local recommendation state (seen/disliked records).
- Kafka: streaming updates for posts/users/interactions/dislikes.
- social-feed-service internal APIs: newest interactions and posts-by-authors.
- friend-service internal API: friend IDs.
- Eureka: service discovery registration.

Main runtime components:

- HTTP API layer (FastAPI endpoints).
- Ranking layer:
  - RecommenderEngine (4-stage feed pipeline),
  - RRFRecommendationEngine (5-source weighted RRF with flow-specific behavior),
  - FeedCandidateService (cross-service source orchestration).
- Vector update layer:
  - post_vectorizer,
  - user_vectorizer,
  - dynamic_vector_service.
- Event consumers:
  - PostEventConsumerWorker,
  - UserEventConsumerWorker,
  - UserInteractionConsumerWorker,
  - PostDislikeConsumerWorker.

---

## 3. Codebase Feature Structure

## app/main.py

- App startup/shutdown lifecycle.
- Eureka register/unregister.
- Mongo recommendation index creation.
- Starts/stops all Kafka worker loops.

## app/api

- health endpoint.
- recommendation endpoints:
  - authenticated feed endpoint,
  - internal feed endpoint,
  - internal RRF feed/reel endpoints,
  - internal manual re-vectorization endpoint.

## app/security

- SecurityContextFilter: reads gateway headers (X-Account-Id, X-User-Id, etc.) and sets request principal.
- SecurityConfigMiddleware: allows internal/public paths, enforces auth for protected paths.

## app/services

- recommender_engine.py: 4-stage semantic + social + popularity ranking pipeline.
- rrf_recommendation_engine.py: weighted RRF + time decay + flow-aware filters + random injection.
- feed_candidate_service.py: builds candidate streams (user vector, peer, friend, trending, random).
- dynamic_vector_service.py: dynamic user vector update and dislike-driven negative feedback.
- post_vectorizer.py: transforms post metadata into embedding text.
- user_vectorizer.py: transforms user profile into baseline embedding text.

## app/workers

- post_event_consumer.py: upsert/delete post vectors from post lifecycle events.
- user_event_consumer.py: upsert baseline user vectors from user-created events.
- user_interaction_consumer.py: re-compute user dynamic vectors after view events.
- post_dislike_consumer.py: persist dislikes and push user vector away from disliked content.

## app/clients

- qdrant_client.py and mongodb_client.py factories.
- social_feed_client.py and friend_service_client.py for internal HTTP integrations.

## app/repositories

- recommendation_repository.py for local MongoDB collections:
  - seen_posts,
  - disliked_posts,
  - startup index setup.

## tests/services

- test_rrf_recommendation_engine.py validates ranking behavior and flow filtering.
- test_dynamic_vector_service.py validates vector update math and fallback handling.

---

## 4. Startup and Runtime Lifecycle

On app startup:

1. Load settings from env (.env + defaults).
2. Configure logging and middleware.
3. Register in Eureka (if enabled).
4. Ensure MongoDB indexes for recommendation collections.
5. Start Kafka worker tasks.

On shutdown:

1. Stop worker tasks gracefully.
2. Unregister from Eureka.

Operational implication:

- If Kafka is disabled in config, worker loops do not start, but API endpoints still run.
- If Eureka registration fails, service continues running (fail-open for local/dev resilience).

---

## 5. Security and Request Identity

Authentication model:

- This service trusts gateway-propagated identity headers.
- SecurityContextFilter maps headers into request.state.user_principal.

Protected vs public:

- Internal paths containing /internal/ are open for service-to-service calls.
- Public paths include health/docs/openapi and configured public endpoints.
- Other paths require resolved user principal or return 401.

Important detail:

- /recommendations/feed is authenticated and uses the principal user.
- Internal recommendation paths are intentionally unauthenticated for backend integration.

---

## 6. API Surface and What Each Endpoint Does

## GET /health

- Liveness check.
- Returns ApiResponse with status=ok.

## GET /recommendations/feed

- Authenticated personalized feed for caller.
- Uses RecommenderEngine.get_personalized_feed.
- Returns FeedResponse (items include debug scores).

## GET /internal/recommendations/feed/{user_id}

- Same behavior as authenticated feed, but user_id provided directly.
- Useful for service-to-service/debug.

## GET /internal/rrf/feed/{user_id}

- Returns ordered FEED/SHARE post IDs only.
- Orchestrates \_build_rrf_feed with PostTypeFlow.FEED_SHARE.
- Used by social-feed-service to hydrate post IDs into PostResponse.

## GET /internal/rrf/reels/{user_id}

- Returns ordered REEL post IDs only.
- Same orchestration with PostTypeFlow.REEL.

## POST /internal/recommendations/users/{user_id}/vectorize

- Manual trigger for dynamic user vector recomputation.
- Calls update_user_vector and returns updated true/false.

---

## 7. Recommendation Logic: Two Pipelines

The code currently has two recommendation paths.

## Path A: RecommenderEngine (FeedResponse payload)

Used by:

- /recommendations/feed
- /internal/recommendations/feed/{user_id}

Stage 1: Recall

- Load user vector from user_vectors in Qdrant.
- If cold-start, use global centroid fallback.
- Query post_vectors ANN for top semantic candidates.

Stage 2: Filter

- Exclude posts in seen_posts (recent window).
- Exclude self-authored posts.

Stage 3: Social signal

- Find nearest users in user_vectors (interest peers).
- Pull peer recent interactions from social-feed-service.
- Count peer interaction support per post.

Stage 4: Rank

- Build three ranked lists:
  - semantic similarity,
  - social count,
  - popularity proxy from stats.
- Fuse using weighted reciprocal rank fusion.
- Return FeedItem objects with semantic/social/popularity/final scores.

## Path B: Internal RRF ID generation (FeedCandidateService + RRFRecommendationEngine)

Used by:

- /internal/rrf/feed/{user_id}
- /internal/rrf/reels/{user_id}

Source assembly:

1. user_vector candidates (Qdrant ANN post hits)
2. peer_posts (posts by nearest-interest users)
3. friend_posts (posts by accepted friends)
4. trending_posts (global latest by flow)
5. random_posts (optional pool)

Then:

- apply flow-aware type filtering,
- apply time decay by source,
- weighted RRF over first 4 sources,
- random injection on top,
- return ranked post ID list.

Why two paths exist:

- Path A returns full FeedResponse with signal transparency.
- Path B is optimized for service-to-service ID ranking and hydration by social-feed-service.

---

## 8. Cold-Start and Resilience Strategy

Cold-start detection occurs when:

- no user vector exists, or
- vector exists but initial interests and interaction history are effectively absent.

Fallback behavior:

- Compute global centroid of sampled post vectors.
- Query post_vectors with centroid.
- Combine with trending source in internal RRF flow.

Graceful degradation:

- If friend-service/social-feed-service calls fail, source list becomes empty, not fatal.
- Ranking continues with remaining sources.
- This avoids blank responses caused by single dependency failure.

---

## 9. Vectorization and Learning Logic

## Post vector indexing

Input events:

- social-feed.post.created
- social-feed.post.updated
- social-feed.post.deleted

Behavior:

- build semantic text from caption, hashtags, location, media types, music.
- upsert vector + metadata into post_vectors.
- on deleted event, remove vector point.

## User baseline vector indexing

Input event:

- user.created

Behavior:

- build profile document from initialInterests and bio.
- upsert into user_vectors.

## Dynamic user vector update

Trigger:

- post view events (social-feed.post.view.recorded),
- manual /vectorize endpoint.

Formula:

- V_int = time-decayed centroid of interacted post vectors.
- V_dynamic = (1 - alpha) _ V_base + alpha _ V_int.
- L2 normalize and upsert to user_vectors.

Decay detail:

- interaction weights decay exponentially by age (half-life config).
- interaction event weight is multiplied into decay weight.

## Negative feedback for dislikes

Trigger:

- social-feed.post.dislike.recorded

Behavior:

1. persist post in disliked_posts (hard exclusion list).
2. apply gradient-like push:
   U_new = Normalize(U_old - eta \* I_disliked).

Effect:

- disliked post and similar vectors become less likely to rank high later.

---

## 10. Data Model and Storage

## Qdrant collections

- post_vectors
  - vector for each post.
  - payload includes post_id, author_id, group_id, post_type, stats, media info, timestamps.
- user_vectors
  - vector for each user.
  - payload includes user_id, profile fields, vector_version, update metadata.

## MongoDB collections (local to recommendation service)

- seen_posts
  - user_id + post_id + seen_at.
  - recent exclusion window for repeat suppression.
- disliked_posts
  - user_id + post_id + disliked_at.
  - persistent hard exclusion list.

Indexing:

- unique user_id+post_id indexes prevent duplicates.
- recency indexes support efficient filtering.

---

## 11. Key Configuration Knobs and Their Impact

Vector and model:

- embedding_model_name
- qdrant_collection_name
- qdrant_user_collection_name

Kafka:

- kafka_post_created_topic
- kafka_post_updated_topic
- kafka_post_deleted_topic
- kafka_user_created_topic
- kafka_post_view_recorded_topic
- kafka_post_dislike_recorded_topic
- consumer group IDs per worker

Dynamic vector behavior:

- user_vector_alpha
  - higher value favors recent behavior over baseline profile.
- user_vector_interaction_limit
  - how many latest interactions are sampled for V_int.
- user_vector_decay_half_life_days
  - lower value emphasizes very recent actions.
- negative_feedback_learning_rate
  - higher value more aggressively pushes vector away from disliked content.

Operational:

- kafka_enabled
- eureka_enabled
- social_feed_service_url
- friend_service_url

---

## 12. End-to-End Flow Examples

## A. New post appears in future recommendations

1. social-feed-service publishes post.created.
2. PostEventConsumerWorker consumes event.
3. post_vectorizer builds text; Qdrant upsert into post_vectors.
4. Subsequent ANN recall can retrieve this post for matching users.

## B. User views post, personalization updates

1. social-feed-service emits post.view.recorded.
2. UserInteractionConsumerWorker batches user IDs and calls update_user_vector.
3. dynamic_vector_service recomputes and upserts user vector.
4. next recommendation query uses updated vector.

## C. User dislikes post

1. social-feed-service emits post.dislike.recorded.
2. PostDislikeConsumerWorker stores disliked post in Mongo.
3. dynamic_vector_service applies negative feedback update.
4. feed builders exclude disliked post IDs and reduce similar content affinity.

---

## 13. Test Coverage and What It Guarantees

Current service tests focus on ranking and vector math:

- test_rrf_recommendation_engine.py
  - flow filtering (FEED/SHARE vs REEL),
  - weighted RRF behavior,
  - dedup and output shape.
- test_dynamic_vector_service.py
  - alpha blend correctness,
  - decay behavior,
  - fallback when interactions/post vectors are missing,
  - normalization safety.

What is not fully covered by unit tests:

- real Kafka consumption loops,
- real Qdrant integration behavior,
- cross-service HTTP resilience with actual dependency services.

Those should be validated with integration tests or local stack smoke tests.

---

## 14. Common Extension Points

Add new ranking source:

1. Fetch/construct source in FeedCandidateService.
2. Add source constant and source weight in RRFRecommendationEngine.
3. Add decay config if source is time-sensitive.
4. Extend tests for fusion behavior.

Tune personalization aggressiveness:

- Increase user_vector_alpha for behavior-first personalization.
- Reduce decay half-life for faster adaptation.
- Increase dislike learning rate if users still see near-similar content.

Add additional user events:

- Extend UserEventConsumerWorker topic bindings.
- Update user_profile_from_event mapping logic.
- Keep payload backward-compatible for existing vectors.

---

## 15. Known Design Tradeoffs

- Two recommendation pathways coexist (FeedResponse path and internal RRF ID path).
  - Pro: flexibility for different consumers.
  - Con: potential divergence if not kept aligned.

- Internal endpoints are open by middleware design.
  - Pro: easy service-to-service calls.
  - Con: requires trusted network/gateway boundaries.

- Global centroid fallback samples vectors, not full collection.
  - Pro: practical runtime cost.
  - Con: centroid quality depends on sample representativeness.

---

## 16. Practical Troubleshooting Checklist

No recommendations returned:

1. Check /health and Eureka registration.
2. Verify user_vectors contains user point.
3. Verify post_vectors has indexed points.
4. Check logs for Qdrant query errors.
5. Confirm social-feed internal endpoints are reachable.

Too many repeated posts:

1. Confirm seen_posts updates are happening.
2. Verify interaction events are arriving and vector update worker is running.
3. Check view exclusion set in feed_candidate_service.

Dislikes not taking effect:

1. Check disliked_posts collection updates.
2. Check post_dislike_consumer logs.
3. Verify apply_negative_feedback success logs and vector_version updates.

---

## 17. Summary

The post-recommendation-service combines:

- event-driven vector indexing,
- online user-vector learning,
- hybrid source orchestration,
- weighted RRF ranking,
- and resilience-first fallbacks.

This architecture allows personalized ranking to remain useful even under partial dependency failure, while preserving clear extension points for future recommendation experiments.
