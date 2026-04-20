# Pull Request

## Jira Ticket

- [BH-216](https://tranngochuyen.atlassian.net/browse/BH-216)

---

## Description

This PR covers the full social-feed-service and post-recommendation-service feature set, including end-to-end enhancements across feed, engagement, moderation, recommendation ranking, and resilience flows.

Business context:

- Deliver complete social posting capability: posts, stories, reels, shares, comments, reactions, reports, and interaction tracking.
- Deliver personalized recommendation capabilities with a Python recommendation engine (vector indexing, hybrid ranking, and fallback-safe candidate generation).
- Increase engagement quality by notifying users for meaningful social events (new friend post, post like, post comment, comment reply).
- Improve moderation reliability and UX through dedicated system-notification delivery.
- Protect feed/reels continuity when recommendation-service is unavailable or returns empty IDs.

---

## Type of change

Please check the relevant option:

- [x] New feature (adds new functionality)
- [x] Bug fix (fixes an existing issue)
- [ ] Refactor (no behavior change)
- [x] Config / Infrastructure change
- [ ] Other (please describe)

---

## What was changed?

- Core social-feed-service capabilities (full scope)
  - Post lifecycle: create, update, soft-delete, get-by-id, my posts, feed/share posts, reels, and grouped stories.
  - Post-type business rules:
    - `SHARE`: preserve original-content linkage, set `rootPostId`/`originalAuthorId`, and increment shared post stats.
    - `STORY`: enforce expiry defaults and story-specific fields.
    - `REEL`: enforce single media and VIDEO-only constraints.
  - Comment lifecycle: create, update, soft-delete, root comments, replies, reply depth/count handling.
  - Reaction lifecycle: toggle/delete/search/stats for post and comment targets with async projection updates.
  - Interaction lifecycle: record idempotent view/dislike actions and query interaction history.
  - Reporting and moderation: user report submission + admin grouped review, target drill-down, moderation action pipeline, and warning history.

- Public API surface covered by social-feed-service
  - `/posts`: create/feed/stories/reels/get-by-id/my-posts/update/delete.
  - `/comments`: create/update/delete/root-by-post/replies.
  - `/reactions`: toggle/delete/search/stats.
  - `/recommendations`: feed/reels recommendations.
  - `/reports` and `/admin/reports`: report submission, moderation processing, grouped reports, user warnings.
  - `/interactions`: interaction history + explicit view/dislike recording.

- Internal API surface (service-to-service)
  - `/internal/posts/by-authors` for recommendation candidate generation.
  - `/internal/interactions/users/{userId}/newest` for latest behavior signals.
  - `/social/internal/seeder` to seed users/interests/posts/comments/reactions/interactions for local/dev testing.

- Event-driven processing and projection updates
  - User sync listeners maintain local `UserSummary` read model (`user.created`, `user.updated`).
  - Reaction projection listener updates denormalized reaction counts and top reactions.
  - Comment projection listener updates post comment counts and triggers comment/reply notifications.
  - Post-view listener updates denormalized `viewCount`.
  - User-interaction listener provides idempotent persistence using topic/partition/offset document IDs.

- Notification and engagement enhancements
  - New post fan-out: `PostCreatedNotificationListener` sends `POST_PUBLISHED` to friend recipients.
  - Comment notifications:
    - `POST_COMMENT` to post owner when a new comment is added.
    - `COMMENT_REPLY` to parent-comment owner on reply.
  - Reaction notifications:
    - `POST_LIKE` when a reaction is active and target type is post.
    - Skips self-notifications and non-post targets.
  - Friend-service integration via `FriendServiceClient` for recipient fan-out.

- Recommendation resiliency and fallback behavior
  - Recommendation feed/reels calls now fallback to Mongo-backed social-feed queries when:
    - recommendation-service throws an exception, or
    - recommendation-service returns empty/null post ID lists.
  - Ensures recommendation endpoints still return usable content during dependency degradation.

- Post-recommendation-service (Python) features and functionality
  - FastAPI service orchestration and lifecycle:
    - Eureka registration/unregistration on startup/shutdown.
    - Automatic startup of background Kafka workers for post, user, view, and dislike event streams.
    - Health endpoint exposed at `/health`.
  - Recommendation APIs:
    - Authenticated endpoint: `/recommendations/feed` for personalized feed response payload.
    - Internal endpoints for service-to-service usage:
      - `/internal/recommendations/feed/{user_id}` for user-specific feed retrieval.
      - `/internal/rrf/feed/{user_id}` and `/internal/rrf/reels/{user_id}` for ranked post ID retrieval by flow.
      - `/internal/recommendations/users/{user_id}/vectorize` for manual dynamic vector refresh.
  - Hybrid ranking and candidate orchestration:
    - `RRFRecommendationEngine` fuses five candidate streams:
      - `user_vector`, `peer_posts`, `friend_posts`, `trending_posts`, `random_posts`.
    - Separate ranking behavior for two flows:
      - `FEED_SHARE` (FEED + SHARE) and `REEL`.
    - Flow-specific source weights and time-decay rates.
    - View/dislike exclusions applied before final ranking to reduce repetition and unwanted content.
  - Cold-start and resiliency strategies:
    - Cold-start detection when user vector/initial interests are missing.
    - Fallback to trending + global centroid ANN query to avoid empty recommendation outputs.
    - Graceful degradation when dependent internal APIs fail (returns partial sources instead of hard failure).
  - Vectorization and feedback learning:
    - Post vector indexing from Kafka post events (`created`, `updated`, `deleted`) into Qdrant `post_vectors`.
    - Baseline user vector indexing from user events into Qdrant `user_vectors`.
    - Dynamic user vector update using alpha-blended baseline + decayed interaction centroid.
    - Negative feedback learning for dislike events:
      - hard filter persistence in MongoDB (`disliked_posts`), and
      - vector adjustment via `U_new = Normalize(U_old - eta * I_disliked)`.
  - Cross-service integrations:
    - Consumes social-feed internal APIs for:
      - newest interactions (`/internal/interactions/users/{userId}/newest`),
      - post retrieval by authors (`/internal/posts/by-authors`).
    - Consumes friend-service internal API for friend candidate expansion.
    - Ensures indexes for recommendation persistence collections on startup.

- Moderation system-notification architecture
  - Moderation notifications migrated from raw pipeline to dedicated system-notification events.
  - Added rich moderation metadata (`targetId`, `targetType`, `targetTypeVi`, optional `adminNote`).
  - Added category-aware system events (`MODERATION`) for clearer downstream delivery/rendering.

- Shared contract and delivery pipeline updates (cross-module)
  - `common` module:
    - Added notification type `POST_PUBLISHED`.
    - Added `SystemNotificationCategory`.
    - Added `SystemNotificationEvent` and `SystemNotificationEventPublisher`.
    - Added Kafka topic property `notificationEvents.system` (`noti.system`).
  - `notification-service` module:
    - Added topic creation/config for `noti.system`.
    - Added `SystemNotificationListener` and `SystemNotificationDeliveryService` pipeline.
    - Persist system notifications, increase unread counters, apply user preferences, deliver via FCM with retries.
    - Added/updated templates for `POST_LIKE`, `POST_COMMENT`, `COMMENT_REPLY`, `POST_PUBLISHED`, and moderation types.

- Seeder and local bootstrap tuning
  - Updated social-feed seeder constants for lower local load while preserving realistic data variety:
    - `TARGET_POSTS` tuned to `700` and `SHARE_POST_TARGET` tuned to `200`.
    - Includes weighted post-type distributions, themed hashtags/captions, and realistic engagement generation.

Key files changed:

- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/listener/PostCreatedNotificationListener.java` (new)
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/listener/PostCommentCountProjectionRequestedListener.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/reaction/ReactionServiceImpl.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/recommendation/RecommendationServiceImpl.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/report/ModerationServiceImpl.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/client/FriendServiceClient.java` (new)
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/PostController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/CommentController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/ReactionController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/RecommendationController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/ReportController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/AdminReportController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/UserInteractionController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/internal/InternalPostController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/internal/InternalUserInteractionController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/controller/internal/DataSeederController.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/post/PostServiceImpl.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/comment/CommentServiceImpl.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/userinteraction/UserInteractionServiceImpl.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/report/ReportServiceImpl.java`
- `social-feed-service/src/main/java/com/bondhub/socialfeedservice/service/seeder/SocialFeedSeederServiceImpl.java`
- `common/src/main/java/com/bondhub/common/enums/NotificationType.java`
- `common/src/main/java/com/bondhub/common/enums/SystemNotificationCategory.java` (new)
- `common/src/main/java/com/bondhub/common/event/notification/SystemNotificationEvent.java` (new)
- `common/src/main/java/com/bondhub/common/publisher/SystemNotificationEventPublisher.java` (new)
- `common/src/main/java/com/bondhub/common/config/kafka/KafkaTopicProperties.java`
- `notification-service/src/main/java/com/bondhub/notificationservices/config/NotificationKafkaTopicConfig.java`
- `notification-service/src/main/java/com/bondhub/notificationservices/config/DataInitializer.java`
- `notification-service/src/main/java/com/bondhub/notificationservices/listener/SystemNotificationListener.java` (new)
- `notification-service/src/main/java/com/bondhub/notificationservices/service/delivery/SystemNotificationDeliveryService.java` (new)
- `notification-service/src/main/java/com/bondhub/notificationservices/service/delivery/SystemNotificationDeliveryServiceImpl.java` (new)
- `notification-service/src/main/java/com/bondhub/notificationservices/enums/BatchWindowConfig.java`
- `notification-service/src/main/java/com/bondhub/notificationservices/service/delivery/strategy/FcmDeliveryStrategy.java`
- `post-recommendation-service/app/main.py`
- `post-recommendation-service/app/api/endpoints/recommendation.py`
- `post-recommendation-service/app/api/endpoints/health.py`
- `post-recommendation-service/app/services/rrf_recommendation_engine.py`
- `post-recommendation-service/app/services/feed_candidate_service.py`
- `post-recommendation-service/app/services/recommender_engine.py`
- `post-recommendation-service/app/services/dynamic_vector_service.py`
- `post-recommendation-service/app/services/post_vectorizer.py`
- `post-recommendation-service/app/services/user_vectorizer.py`
- `post-recommendation-service/app/workers/post_event_consumer.py`
- `post-recommendation-service/app/workers/user_event_consumer.py`
- `post-recommendation-service/app/workers/user_interaction_consumer.py`
- `post-recommendation-service/app/workers/post_dislike_consumer.py`
- `post-recommendation-service/app/clients/social_feed_client.py`
- `post-recommendation-service/app/clients/friend_service_client.py`
- `post-recommendation-service/app/core/config.py`
- `post-recommendation-service/tests/services/test_rrf_recommendation_engine.py`
- `post-recommendation-service/tests/services/test_dynamic_vector_service.py`

---

## How Has This Been Tested? (Manual)

Please describe how reviewers can verify this change.

1. Run backend locally
   - Start Kafka + MongoDB + social-feed-service + notification-service + friend-service.

- Start post-recommendation-service for recommendation path validation.
- Stop post-recommendation-service to validate social-feed fallback behavior.

2. Verify core post/feed APIs
   - `POST /posts` create FEED/REEL/STORY/SHARE variants.
   - `GET /posts/feed`, `GET /posts/reels`, `GET /posts/stories`, `GET /posts/me`, `GET /posts/{postId}`.
   - Verify type-specific behavior (story expiry, reel media validation, share preview linkage).

3. Verify comments and reactions
   - Comment on a post and reply to a comment:
     - Verify post `commentCount` projection updates.
     - Verify `POST_COMMENT` and `COMMENT_REPLY` notifications to correct recipients.
   - Toggle reaction on post/comment:
     - Verify reaction counts/top reactions projection updates.
     - Verify `POST_LIKE` only for active post reactions and non-self actors.

4. Verify recommendation behavior and fallback
   - Call `GET /recommendations/feed?size=10` and `GET /recommendations/reels?size=10`.
   - With recommendation-service available: verify hydrated recommended IDs are returned.
   - With recommendation-service unavailable/empty response: verify Mongo fallback returns non-empty feed/reels when data exists.

5. Verify moderation flow
   - Submit report via `POST /reports`.
   - Process via admin `POST /admin/reports/action` with DELETE_CONTENT / HIDE_CONTENT / WARN_USER.
   - Verify content status changes, warning persistence, and moderation notifications sent via `noti.system`.

6. Verify notification-service delivery
   - Confirm notification records persisted for recipients and unread count incremented.
   - Confirm FCM/in-app template rendering for `POST_PUBLISHED`, `POST_LIKE`, `POST_COMMENT`, `COMMENT_REPLY`, and moderation notification types.

7. Verify internal integration APIs
   - `GET /internal/posts/by-authors` returns recent posts for supplied author IDs.
   - `GET /internal/interactions/users/{userId}/newest` returns latest interactions.
   - `POST /social/internal/seeder/seed/all` executes full seeding pipeline and returns summary.

8. Verify post-recommendation-service behavior
   - Health and registration
     - `GET /health` on post-recommendation-service returns status `ok`.
     - Verify Eureka registration for `post-recommendation-service`.
   - Recommendation endpoints
     - Call `/internal/rrf/feed/{userId}?n=20` and `/internal/rrf/reels/{userId}?n=20`.
     - Verify non-empty ordered post ID output when vectors/candidates exist.
   - Dynamic vector updates
     - Trigger `/internal/recommendations/users/{userId}/vectorize`.
     - Verify success response and updated vector metadata in `user_vectors` payload.
   - Event ingestion and indexing
     - Produce/trigger post created/updated/deleted events and verify `post_vectors` upsert/delete in Qdrant.
     - Produce/trigger user created events and verify `user_vectors` upsert in Qdrant.
     - Trigger post view/dislike events and verify interaction-driven vector refresh and dislike persistence in MongoDB.

---

## Risk Level

- [ ] Low - safe logic change
- [x] Medium - affects business logic and event-driven flows
- [ ] High - affects auth / data integrity

---

## Checklist

- [ ] PR title contains `[BH-KEY]`
- [ ] Commit messages follow convention
- [x] Jira ticket is linked
- [x] Input validation handled
- [x] Error cases handled
- [x] Manual testing completed
