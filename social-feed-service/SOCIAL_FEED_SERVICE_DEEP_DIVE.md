# Social Feed Service Deep Dive

This document explains how social-feed-service works in depth:

- architecture and feature structure,
- core domain and API behavior,
- event-driven processing,
- recommendation and notification integration,
- moderation and operational concerns.

It is intended for engineers maintaining or extending the service.

---

## 1. Service Purpose and Boundaries

social-feed-service is the primary social domain service for posts and engagement.

It owns:

- post lifecycle (feed, share, story, reel),
- comment lifecycle and nested replies,
- reaction lifecycle and denormalized reaction statistics,
- user interaction records (view/dislike and interaction history),
- report submission and moderation action workflows,
- internal APIs consumed by recommendation services,
- event publishing for downstream analytics/recommendation/notification pipelines.

It does not own:

- recommendation ranking algorithms (post-recommendation-service owns ranking),
- notification template rendering and multi-channel delivery (notification-service owns delivery),
- auth token issuance (auth-service/gateway layer),
- user profile master records (user-service owns source of truth).

---

## 2. Runtime Architecture

Core runtime stack:

- Spring Boot service with MongoDB repositories.
- Feign clients for service-to-service calls.
- Kafka producers (via outbox publisher) and Kafka consumers (listeners).
- SecurityContextFilter from common module with stateless security.

Startup characteristics:

- Feign enabled for cross-service calls.
- Mongo auditing enabled.
- Async and scheduling enabled.
- Default timezone set to Asia/Ho_Chi_Minh.

---

## 3. Feature-Oriented Package Structure

## controller

Public APIs:

- PostController
- CommentController
- ReactionController
- RecommendationController
- ReportController
- AdminReportController
- UserInteractionController

Internal APIs:

- InternalPostController
- InternalUserInteractionController
- DataSeederController

## service

- post: post business logic and type-specific constraints.
- comment: comment/reply lifecycle.
- reaction: reaction toggle/delete/stats and notification trigger.
- recommendation: recommendation-service delegation with fallback.
- userinteraction: interaction persistence and event publishing.
- report: report creation and moderation actions.
- seeder: data generation and bootstrap pipeline.

## publisher

- Emits post/comment/reaction/view/dislike/interaction events through outbox-driven publishing.

## listener

- Consumes Kafka events to project denormalized counters and sync local read models.

## repository

- Mongo repositories for posts/comments/reactions/reports/user interactions and aggregations.

## mapper

- DTO/entity transformations and media URL resolution.

## client

- Feign integrations with post-recommendation-service, friend-service, auth-service, and user-service.

---

## 4. Domain Model and Persistence

Primary Mongo documents:

- posts
- comments
- reactions
- user_interactions
- reports
- user_warnings
- user_summaries
- hashtags

Notable domain behavior:

- soft delete pattern via active flag.
- post hidden flag for moderation-only visibility changes.
- post TTL semantics for stories via expiresAt index behavior.
- unique reaction identity per user-target-targetType.
- idempotent interaction persistence in listener path using topic:partition:offset document IDs.

Denormalized stats pattern:

- Post.stats includes reactionCount, commentCount, shareCount, viewCount, topReactions.
- Comment includes reactionCount and replyCount.
- Projection listeners recalculate counters asynchronously from event streams.

---

## 5. API Surface and Capability Map

## Posts

- POST /posts
- GET /posts/feed
- GET /posts/stories
- GET /posts/reels
- GET /posts/{postId}
- GET /posts/me
- PUT /posts/{postId}
- DELETE /posts/{postId}

## Comments

- POST /comments
- PUT /comments/{commentId}
- DELETE /comments/{commentId}
- GET /comments/post/{postId}
- GET /comments/{commentId}/replies

## Reactions

- POST /reactions/toggle
- DELETE /reactions
- GET /reactions/search
- GET /reactions/stats

## Recommendations

- GET /recommendations/feed
- GET /recommendations/reels

## Interactions

- GET /interactions/me
- GET /interactions/posts/{postId}
- POST /interactions/posts/{postId}/view
- POST /interactions/posts/{postId}/dislike

## Reports and Moderation

- POST /reports
- GET /reports/my
- GET /admin/reports
- GET /admin/reports/target/{targetType}/{targetId}
- POST /admin/reports/action
- GET /admin/reports/warnings/{userId}

## Internal APIs

- GET /internal/posts/by-authors
- GET /internal/interactions/users/{userId}/newest
- POST /social/internal/seeder/seed/all

Security note:

- Paths containing /internal/ are explicitly permitted and expected to be used within trusted service boundaries.

---

## 6. Core Business Logic by Domain

## 6.1 Post lifecycle logic

Key rules in PostServiceImpl:

- On create/update/delete, domain events are published.
- SHARE posts:
  - resolve and validate shared post,
  - keep sharedCaption semantics,
  - set rootPostId and originalAuthorId,
  - increment shareCount on original content.
- STORY posts:
  - default expiresAt if not provided,
  - story-specific fields retained.
- REEL posts:
  - exactly one media required,
  - media type must be VIDEO.
- Non-story/non-reel content clears fields not applicable to type.
- getPostsByIds preserves input ordering to keep recommendation ranking order stable.

## 6.2 Comment lifecycle logic

Key rules in CommentServiceImpl:

- Validates post existence for comment creation.
- Supports parent-child replies with replyDepth and parent replyCount updates.
- Emits comment-count projection events for increment/decrement.
- Emits interaction events for recommendation signal generation.
- Root comment listing supports NEWEST or MOST_REACTED styles.

## 6.3 Reaction lifecycle logic

Key rules in ReactionServiceImpl:

- Toggle behavior computes desiredActive from existing state.
- Supports post and comment targets.
- Emits projection command events for async counter updates.
- Emits interaction events for recommendation behavior signal.
- Publishes POST_LIKE notification only when:
  - target is POST,
  - reaction becomes active,
  - actor is not the same as post author.

## 6.4 Interaction logic

Key rules in UserInteractionServiceImpl:

- recordView and recordDislike are idempotent per user+post+type.
- Writes interaction records immediately.
- Emits POST_VIEW_RECORDED and POST_DISLIKE_RECORDED events for downstream consumers.
- Provides internal newest-interaction query used by recommendation service.

## 6.5 Recommendation integration logic

Key rules in RecommendationServiceImpl:

- Delegates ranked post ID retrieval to post-recommendation-service internal endpoints.
- Hydrates returned IDs into PostResponse preserving ranking order.
- Falls back to Mongo-backed feed/reels queries when recommendation service fails or returns empty IDs.

This provides graceful degradation and avoids blank recommendation feeds.

## 6.6 Reporting and moderation logic

ReportServiceImpl:

- Validates report target exists.
- Blocks self-reporting.
- Blocks duplicate pending reports for same reporter and target.

ModerationServiceImpl:

- Processes all pending reports for a target in bulk.
- Supports actions:
  - DELETE_CONTENT,
  - HIDE_CONTENT,
  - WARN_USER,
  - DISMISS_REPORT.
- Writes admin action metadata to all pending reports.
- Publishes moderation notifications through SystemNotificationEventPublisher with category MODERATION.

Aggregation repository:

- Builds grouped report views and target-level details via Mongo aggregation pipelines.

---

## 7. Event-Driven Architecture

social-feed-service is both an event producer and consumer.

## 7.1 Produced events

Publishers emit events through OutboxEventPublisher-backed flow.

Produced event families include:

- post created/updated/deleted,
- post comment count projection requested,
- reaction toggle command requested,
- user interaction recorded,
- post view recorded,
- post dislike recorded.

Design intent:

- write-side operations remain responsive,
- projections and downstream effects run asynchronously,
- event reliability is improved through outbox pattern usage.

## 7.2 Consumed events and projections

UserSyncListener:

- consumes user.created and user.updated,
- upserts local user_summaries read model used for author enrichment.

ReactionToggleCommandRequestedListener:

- consumes reaction projection commands,
- recalculates reaction counts,
- updates topReactions on posts,
- updates comment reactionCount.

PostCommentCountProjectionRequestedListener:

- consumes comment count projection events,
- recalculates post commentCount,
- emits POST_COMMENT and COMMENT_REPLY notifications on increment paths.

PostViewRecordedListener:

- consumes post view recorded events,
- increments post viewCount projection.

UserInteractionRecordedListener:

- consumes user.interaction events,
- persists interaction documents with idempotency guard.

PostCreatedNotificationListener:

- consumes post.created,
- fetches friend IDs,
- fans out POST_PUBLISHED raw notification events.

---

## 8. Notification Integration Model

There are two notification publishing paths in social-feed-service:

Raw notification path:

- used for engagement notifications (POST_PUBLISHED, POST_LIKE, POST_COMMENT, COMMENT_REPLY),
- published via RawNotificationEventPublisher,
- consumed and rendered by notification-service pipeline.

System notification path:

- used for moderation outcomes (CONTENT_REMOVED, CONTENT_HIDDEN, USER_WARNED),
- published as SystemNotificationEvent with category MODERATION,
- consumed by notification-service system listener pipeline.

This split separates engagement stream events from admin/system communication events.

---

## 9. External Service Integrations

post-recommendation-service:

- fetch ranked feed/reel IDs,
- optional manual re-vectorization endpoint available through client contract.

friend-service:

- internal friend ID retrieval for post publication fan-out.

auth-service and user-service:

- used by seeding pipeline to map account and user summaries,
- used to seed user interests for recommendation bootstrap scenarios.

---

## 10. Security Model

SecurityConfig:

- stateless session policy.
- internal endpoints permitted.
- docs/actuator/public endpoints permitted.
- all other endpoints require authenticated security context.

Method-level authorization:

- admin moderation API protected by @PreAuthorize(hasRole('ADMIN')).

CORS:

- broad origin/method/header configuration enabled for service accessibility.

---

## 11. Seeder Pipeline and Local Data Bootstrap

SocialFeedSeederServiceImpl provides one-shot seedEverything pipeline that:

1. fetches accounts/users from auth/user services,
2. seeds user interests for recommendation readiness,
3. creates posts/comments/reactions/interactions with realistic distributions,
4. publishes post events for indexing side-effects.

Current tuning values are reduced for local bootstrap practicality while retaining diversity.

---

## 12. Typical End-to-End Flows

## A. New post publish and friend notification

1. User creates post via POST /posts.
2. Post persisted and POST_CREATED emitted.
3. PostCreatedNotificationListener consumes event.
4. friend-service queried for recipients.
5. POST_PUBLISHED raw notifications fan out to friends.

## B. Comment and reply notifications

1. User creates comment/reply.
2. Comment count projection event emitted.
3. PostCommentCountProjectionRequestedListener updates post commentCount.
4. POST_COMMENT or COMMENT_REPLY notifications emitted as appropriate.

## C. Reaction projection and like notification

1. User toggles reaction.
2. Reaction state persisted.
3. projection and interaction events emitted.
4. ReactionToggleCommandRequestedListener recalculates counters.
5. POST_LIKE notification emitted when criteria are met.

## D. Recommendation fetch with fallback

1. Client calls /recommendations/feed or /recommendations/reels.
2. Service asks post-recommendation-service for ranked IDs.
3. IDs hydrated to full posts preserving ranking order.
4. If remote failure or empty IDs, fallback returns Mongo-backed feed/reels.

## E. Moderation action and system notification

1. Admin executes /admin/reports/action.
2. Content action applied (delete/hide/warn/dismiss).
3. Pending reports resolved/dismissed in bulk.
4. System moderation notification event published.

---

## 13. Operational Notes and Tradeoffs

- Internal endpoints are intentionally open and rely on trusted internal network boundaries.
- Asynchronous projections improve latency but can cause short-lived eventual consistency windows.
- Denormalized counters are event-driven and can lag briefly after writes.
- Recommendation fallback prioritizes user experience continuity over strict dependency coupling.

---

## 14. Extension Points

Add new interaction signal:

- extend InteractionType usage in publishers/listeners,
- persist in user_interactions,
- propagate to recommendation service contract if needed.

Add new notification type:

- define enum in common module,
- publish from relevant domain service/listener,
- add templates and delivery logic in notification-service.

Add new recommendation flow type:

- extend post-recommendation-service internal endpoint contract,
- adapt RecommendationServiceImpl hydration and fallback policy.

Add moderation policies:

- extend AdminAction and moderation service branching,
- extend report aggregation DTOs where needed,
- map to suitable system notification category/type.

---

## 15. Troubleshooting Checklist

Feed recommendations empty:

1. Check recommendation service availability and logs.
2. Verify internal recommendation endpoints reachable.
3. Confirm fallback feed/reels Mongo queries return data.

Counters not updated:

1. Check Kafka listener consumption status.
2. Verify reaction/comment/view events are emitted.
3. Verify listener ack and exception logs.

Notifications missing:

1. Check raw/system publisher logs in social-feed-service.
2. Verify notification-service topic consumers and templates.
3. Confirm recipient IDs and actor IDs are non-empty.

Moderation results inconsistent:

1. Ensure target has pending reports before action.
2. Verify report aggregation outputs and status transitions.
3. Verify content active/hidden flags after action.

---

## 16. Summary

social-feed-service is a domain-rich social backend with:

- strong write-side APIs,
- asynchronous projection and notification flows,
- recommendation integration with graceful fallback,
- moderation workflows with system-level notifications,
- and internal APIs that enable personalization pipelines.

Its design balances feature breadth with operational resilience by combining synchronous REST operations for user actions and asynchronous event-driven updates for projections and downstream processing.
