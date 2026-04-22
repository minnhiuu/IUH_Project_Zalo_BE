# Pull Request

## Jira Ticket

- [BH-xxx]&#58; Refactor `search-service` from single-index user-only design to multi-index Elasticsearch architecture
- [BH-xxx]&#58; Introduce generic monitor/sync abstraction for search indexes
- [BH-xxx]&#58; Add message index monitoring and reindex orchestration
- [BH-xxx]&#58; Reorganize search-service package structure by index capability

---

## Description

This PR refactors `search-service` from an initial **user-only Elasticsearch module** into a more scalable **multi-index search architecture**.

Originally, the service was designed around a single `users` index, so most Elasticsearch admin, monitoring, and reindexing logic lived in a flat service structure and was tightly coupled to the user domain. As message indexing and message search were added later, the previous structure became difficult to extend cleanly.

This refactor introduces a **generic index architecture** that separates:

- shared Elasticsearch infrastructure
- monitoring capabilities
- synchronization/reindex capabilities
- per-index implementations (`USER`, `MESSAGE`)
- admin orchestration and type-based dispatch

The result is a cleaner, more extensible design that allows new search indexes to be added with minimal changes to the admin and orchestration layers. :contentReference[oaicite:0]{index=0} :contentReference[oaicite:1]{index=1} :contentReference[oaicite:2]{index=2}

---

## Type of change

- [x] Refactor
- [x] New feature
- [x] Architecture improvement
- [x] Package reorganization
- [ ] Breaking config / infrastructure change

---

## Technical Details & Architecture

### 1. Refactor from single-index design to multi-index model

The previous design assumed that Elasticsearch administration was centered around the `users` index. This PR generalizes the architecture so that each index type can expose its own monitor and sync capabilities while still sharing common infrastructure.

Two index types are now modeled explicitly through `SearchIndexType`, and orchestration is routed by type instead of hardcoded user-specific service wiring. :contentReference[oaicite:3]{index=3}

---

### 2. Introduced a shared Elasticsearch infrastructure base

A new `AbstractBaseElasticsearchService` was added to centralize reusable Elasticsearch utility logic such as:

- alias resolution
- physical index timestamp parsing
- store size formatting

This prevents duplication across monitor and sync services and provides a stable foundation for all index-specific implementations. :contentReference[oaicite:4]{index=4}

---

### 3. Separated monitoring capability from synchronization capability

To better align with ISP and future extensibility, this PR introduces two distinct contracts:

#### `SearchIndexMonitor`

Provides read-only monitoring capabilities for an index:

- `getHealth()`
- `getStats()`
- `getAllPhysicalIndexes()`
- failed-event monitoring methods

#### `SearchIndexSynchronizer`

Provides operational sync capabilities:

- `reindexAll()`
- `getReindexStatus()`
- `compareWithDatabase()`
- `getDocument()`
- `switchAlias()`

This separation allows an index to expose monitoring only, synchronization only, or both, depending on business needs. :contentReference[oaicite:5]{index=5} :contentReference[oaicite:6]{index=6}

---

### 4. Added abstract monitor and sync service layers

#### `AbstractMonitorService`

A generic base class for monitoring-related Elasticsearch operations. It implements `SearchIndexMonitor` and provides shared behavior such as:

- cluster/index health inspection
- index stats
- physical index listing
- failed-event aggregation integration

#### `AbstractSyncService<T>`

A generic base class for reindex and operational sync behavior. It implements `SearchIndexSynchronizer` and centralizes:

- reindex task creation
- type-aware running-task validation
- async reindex execution
- physical index creation
- alias switching
- old index cleanup
- status tracking per index type

This allows concrete services to focus only on domain-specific data fetching and document mapping. :contentReference[oaicite:7]{index=7} :contentReference[oaicite:8]{index=8}

---

### 5. Added type-aware reindex task tracking

`ReindexTaskTracker` now tracks task status by `SearchIndexType`, ensuring that reindex execution is isolated per index type rather than globally blocking all index operations. This is especially important as `USER` and `MESSAGE` now coexist under the same admin layer. :contentReference[oaicite:9]{index=9}

---

### 6. Added orchestrator for index dispatching

A new `SearchIndexOrchestrator` was introduced as the central dispatch layer.

It automatically collects:

- all `SearchIndexSynchronizer` implementations
- all `SearchIndexMonitor` implementations

and maps them by `SearchIndexType`.

This enables generic admin operations such as:

- reindex by type
- get status by type
- get document by type
- switch alias by type
- fetch monitor data by type

without hardcoding logic per index in the controller. :contentReference[oaicite:10]{index=10}

---

### 7. Added message index monitor and sync integration

This PR extends the architecture beyond `USER` by adding `MESSAGE` support as a first-class search index.

#### `MessageMonitorServiceImpl`

Provides monitoring support for the message index through the generic monitor abstraction. :contentReference[oaicite:11]{index=11}

#### `MessageSyncServiceImpl`

Implements message-specific reindex behavior:

- fetch message batches from `message-service`
- map API responses into `MessageIndex`
- bulk index documents into Elasticsearch
- compare Elasticsearch count against database count

This makes the message index participate fully in the same reindex and monitoring pipeline as the user index. :contentReference[oaicite:12]{index=12}

---

### 8. Kept user-specific sync/search while aligning it with the new generic model

The existing user search and user sync behavior remain intact, but user sync now participates through `SearchIndexSynchronizer` rather than a standalone user-only admin flow.

`UserSyncService` now extends the generic sync contract, and `UserSyncServiceImpl` extends the new abstract sync layer. This preserves existing behavior while aligning the user index with the same extensible architecture used by messages.

---

### 9. Reorganized package structure for maintainability

The service layer was reorganized into capability-based packages:

- `service.index.core`
- `service.index.admin`
- `service.index.message`
- `service.failevent`

This refactor improves discoverability and reflects the new architecture more clearly than the original flat service layout that was built when only user search existed. :contentReference[oaicite:15]{index=15} :contentReference[oaicite:16]{index=16} :contentReference[oaicite:17]{index=17} :contentReference[oaicite:18]{index=18}

---

### 10. Updated admin APIs for generic index operations

`ElasticsearchAdminController` now supports type-based index operations such as:

- `POST /search/elasticsearch/reindex/{type}`
- `GET /search/elasticsearch/reindex/{type}/status/{taskId}`
- `GET /search/elasticsearch/index/{type}/summary`
- `GET /search/elasticsearch/index/{type}/stats`
- `GET /search/elasticsearch/index/{type}/physical-indexes`
- `GET /search/elasticsearch/index/{type}/document/{id}`
- `GET /search/elasticsearch/index/{type}/failed-events`

This replaces the previous user-centric admin workflow with a reusable, multi-index admin surface. At the same time, legacy user-specific operations such as single-user reindex remain available for backward compatibility. :contentReference[oaicite:19]{index=19} :contentReference[oaicite:20]{index=20}

---

### 11. Improved failed-event handling integration

Failed event handling was also refined and grouped under its own package. The service now supports:

- paged filtering through `FailedEventFilter`
- retry by id
- retry in bulk
- retry all by selected index topics
- topic-aware counting

This integrates naturally with index monitoring because failed events can now be surfaced per index type through the monitoring contract. :contentReference[oaicite:21]{index=21} :contentReference[oaicite:22]{index=22} :contentReference[oaicite:23]{index=23}

---

## Business Rules & Validation

| Rule                                                                          | Implementation                                                                        |
| ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| Reindex must be isolated by index type                                        | `ReindexTaskTracker.isReindexRunning(SearchIndexType)`                                |
| Index operations must be type-safe                                            | `SearchIndexType` + orchestrator dispatch                                             |
| Monitoring and synchronization should not be tightly coupled                  | `SearchIndexMonitor` and `SearchIndexSynchronizer` split                              |
| Shared Elasticsearch infra must not be duplicated                             | `AbstractBaseElasticsearchService` / `AbstractMonitorService` / `AbstractSyncService` |
| Backward compatibility for user-specific administration must remain available | legacy `ElasticsearchAdminService` endpoints retained                                 |
| Failed events must be filterable and retryable by topic/type                  | `FailedEventFilter` + topic-aware retry flow                                          |

:contentReference[oaicite:24]{index=24} :contentReference[oaicite:25]{index=25} :contentReference[oaicite:26]{index=26} :contentReference[oaicite:27]{index=27} :contentReference[oaicite:28]{index=28}

---

## API Changes

### New / Generic Admin Endpoints

- `POST /search/elasticsearch/reindex/{type}`
- `GET /search/elasticsearch/reindex/{type}/status/{taskId}`
- `GET /search/elasticsearch/index/{type}/summary`
- `GET /search/elasticsearch/index/{type}/stats`
- `GET /search/elasticsearch/index/{type}/physical-indexes`
- `GET /search/elasticsearch/index/{type}/failed-events`
- `GET /search/elasticsearch/index/{type}/document/{id}`

### Existing / Backward-Compatible Endpoints Retained

- `GET /search/elasticsearch/health`
- `POST /search/elasticsearch/reindex/users/{userId}`

:contentReference[oaicite:29]{index=29}

---

## Why this refactor was necessary

When `search-service` was first introduced, only the `users` index existed, so the original flat service design was sufficient. As message indexing and broader Elasticsearch administration were added, the old structure started to mix:

- index-specific logic
- shared Elasticsearch infra
- admin orchestration
- failed-event handling

This PR addresses that growth by introducing a proper multi-index architecture rather than continuing to duplicate user-centric patterns for every new index type. The new design is more scalable, more maintainable, and better aligned with future additions such as new search domains or monitor-only indexes. :contentReference[oaicite:30]{index=30} :contentReference[oaicite:31]{index=31} :contentReference[oaicite:32]{index=32}

---

## How Has This Been Tested? (Manual)

| Test Case                                | Expected Result                                                     |
| ---------------------------------------- | ------------------------------------------------------------------- |
| Trigger reindex for `USER`               | new task is created and tracked under `USER` type                   |
| Trigger reindex for `MESSAGE`            | new task is created and tracked under `MESSAGE` type                |
| Fetch index summary by type              | correct health, stats, compare, and failed-event count are returned |
| Fetch failed events by type              | only topic-relevant failed events are returned                      |
| Fetch document by type                   | document is resolved through the correct synchronizer               |
| Switch alias by type                     | alias updates are routed to the correct index handler               |
| Call legacy user single-document reindex | existing behavior still works                                       |

---

## Risk Level

- [x] Medium – impacts Elasticsearch admin flow, task orchestration, and package structure

**Rationale:**  
This PR changes the architectural foundation of `search-service` by introducing generic abstractions and type-based orchestration. Although the refactor improves long-term maintainability, it affects core Elasticsearch admin paths and requires careful regression validation for both `USER` and `MESSAGE` flows. :contentReference[oaicite:33]{index=33} :contentReference[oaicite:34]{index=34} :contentReference[oaicite:35]{index=35}

---

## Notes

- The old user-centric admin design is intentionally preserved in a limited form for backward compatibility while the new generic orchestration model becomes the default path.
- This PR is a foundational refactor to support future search index expansion without requiring repeated service/controller rewrites.
- The architecture now supports adding a new index type primarily by implementing monitor/sync contracts and registering a new `SearchIndexType`, rather than modifying controller dispatch logic. :contentReference[oaicite:36]{index=36} :contentReference[oaicite:37]{index=37}
