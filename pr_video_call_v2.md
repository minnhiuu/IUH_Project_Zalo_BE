# Pull Request

## Jira Ticket

- [BH-265](https://tranngochuyen.atlassian.net/browse/BH-265)

---

## Description

This PR introduces two features:

1. **Video Call 1-on-1 (Backend + Web):** Full video call lifecycle using ZEGOCLOUD — initiate, accept, reject, end, cancel — with Redis-based BUSY state management, Kafka-based call signaling via WebSocket, push notifications, and call history saved as system messages.

2. **Sync Contacts + Friend Suggestions (Backend + App):** Device contact import from mobile app, phone/email matching via Elasticsearch, Neo4j contact graph (`IN_CONTACT` relationships), and unified friend suggestions combining mutual friends, shared groups, and contact score.

---

## Type of change

- [x] New feature (adds new functionality)

---

## What was changed?

### Feature 1: Video Call (Backend + Web)

#### 1.1 Call APIs — `CallController` (6 endpoints, base path `/calls`)

| Endpoint | Method | Request Body | Action |
| --- | --- | --- | --- |
| `POST /calls/initiate` | `initiateCall` | `CallRequest { receiverId }` | Create call session, set BUSY in Redis (60s TTL), send push notification via Kafka (`noti.raw`) |
| `POST /calls/{sessionId}/accept` | `acceptCall` | — | Set status → `IN_PROGRESS`, extend BUSY to 2h, send `ACCEPTED` signal via Kafka (`socket-events`) |
| `POST /calls/{sessionId}/reject` | `rejectCall` | — | Set status → `REJECTED`, clear BUSY, send `REJECTED` signal, save system message |
| `POST /calls/{sessionId}/end` | `endCall` | — | Set status → `ENDED`, clear BUSY, send `ENDED` signal, save system message with duration |
| `POST /calls/{sessionId}/cancel` | `cancelCall` | — | Set status → `MISSED`, clear BUSY, send `CANCELLED` signal, save system message |
| `GET /calls/{sessionId}/token` | `getToken` | — | Get/refresh RTC token for the call room |

#### 1.2 New Model: `CallSession`

```java
@Document(collection = "call_sessions")
public class CallSession extends BaseModel {
    String id;
    String callerId, callerName, callerAvatar;
    String receiverId, receiverName, receiverAvatar;
    String roomId;            // format: "call_" + UUID (first 12 hex chars)
    CallStatus status;        // RINGING, IN_PROGRESS, ENDED, MISSED, REJECTED, CANCELLED
    LocalDateTime startTime;  // set on accept (actual call start)
    LocalDateTime endTime;    // set on end/reject/cancel
}
```

**Compound indexes:** `{callerId, status}`, `{receiverId, status}`

#### 1.3 DTOs

**`CallRequest`** (record):

| Field | Type | Validation |
| --- | --- | --- |
| `receiverId` | `String` | `@NotBlank` |

**`CallResponse`** (record):

| Field | Type |
| --- | --- |
| `sessionId` | `String` |
| `roomId` | `String` |
| `rtcToken` | `String` |
| `appId` | `long` |
| `callerId`, `callerName`, `callerAvatar` | `String` |
| `receiverId`, `receiverName`, `receiverAvatar` | `String` |

#### 1.4 Redis Key Patterns & TTLs

| Key Pattern | Value | TTL | Phase |
| --- | --- | --- | --- |
| `user:status:{userId}` | `"BUSY"` | **60 seconds** | Ringing (set on initiate for caller) |
| `user:status:{userId}` | `"BUSY"` | **2 hours** | In-call (extended on accept for both users) |
| _(deleted)_ | — | — | Cleared on reject / end / cancel for both users |

#### 1.5 Kafka Events

| Topic | Event Type | When | Payload |
| --- | --- | --- | --- |
| `socket-events` | `CALL_SIGNAL` (→ `/queue/call-signals`) | On accept/reject/end/cancel | `{ sessionId, signal: "ACCEPTED\|REJECTED\|ENDED\|CANCELLED", roomId }` |
| `socket-events` | `MESSAGE` (→ `/queue/messages`) | After saving call message | `ChatNotification` to both conversation members |
| `noti.raw` | `CALL` | On initiateCall | `{ sessionId, roomId, callerName, callerAvatar }` |

#### 1.6 Call System Messages

All saved as `MessageType.CALL` with metadata:

| `callAction` | Content | `durationSeconds` |
| --- | --- | --- |
| `"ended"` | `"Cuộc gọi video - X phút Y giây"` | actual seconds |
| `"missed"` | `"Cuộc gọi nhỡ"` | 0 |
| `"rejected"` | `"Cuộc gọi bị từ chối"` | 0 |

**Message metadata:** `{ callAction, sessionId, durationSeconds, callerId, callerName, receiverId, receiverName }`

#### 1.7 Error Codes

| Error Code | HTTP | Code | Description |
| --- | --- | --- | --- |
| `CALL_SELF_NOT_ALLOWED` | 400 | 5005 | Cannot call yourself |
| `CALL_USER_NOT_FOUND` | 404 | 5002 | Receiver not found |
| `CALL_USER_BUSY` | 409 | 5001 | Receiver is already in a call |
| `CALL_ALREADY_IN_PROGRESS` | 409 | 5004 | Caller is already in a call |
| `CALL_SESSION_NOT_FOUND` | 404 | 5003 | Call session not found |
| `CALL_TOKEN_GENERATION_FAILED` | 500 | 5006 | Zego token generation failed |
| `CALL_BLOCKED` | 403 | 2305 | Call blocked by user |

**Additional:** `UNAUTHORIZED` if non-receiver tries to accept; circuit breaker fallback on user-service calls.

#### 1.8 Business Rules

1. Self-call prevented (`callerId == receiverId`)
2. Both caller and receiver BUSY checked via Redis before initiating
3. Only the receiver can accept a call (authorization check)
4. Room ID generated as `"call_" + UUID.first12Hex()`
5. On accept: `startTime` reset to `now()`, BUSY extended to 2h for both users
6. On reject/end/cancel: BUSY cleared for both users, system message saved to conversation
7. `initiateCall` wrapped with Resilience4j `@CircuitBreaker(name = "userService")`

#### 1.9 Zego Config

```yaml
zego:
  app-id: ${ZEGO_APP_ID}
  server-secret: ${ZEGO_SERVER_SECRET}
```

#### 1.10 Web Frontend Changes

| File | Description |
| --- | --- |
| `VideoCallRoom` | Full-screen call UI using `@zegocloud/zego-uikit-prebuilt` (1-on-1 mode) |
| `OutgoingCallScreen` | Ringing UI with camera preview while waiting for receiver |
| `IncomingCallDialog` | Accept/reject dialog for incoming calls via WebSocket |
| `useVideoCall()` hook | State machine: idle → ringing → active, handles all call signals |
| `call.api.ts` | API client for all 6 call endpoints |
| `call-message.tsx` | Renders call history messages (duration, missed, rejected) in chat |
| `system-message.tsx` | Updated to support `video_call` message type |
| i18n (en/vi) | Translations for all call-related labels |

---

### Feature 2: Sync Contacts + Friend Suggestions (Backend + App)

#### 2.1 Contact Import API — `ContactController` (1 endpoint)

| Endpoint | Method | Request Body | Action |
| --- | --- | --- | --- |
| `POST /contacts/import` | `importContacts` | `ContactImportRequest` | Import device contacts, match against registered users, create Neo4j `IN_CONTACT` relationships |

#### 2.2 Friend Suggestion & Mutual Friends APIs — `FriendshipController` (5 endpoints)

| Endpoint | Method | Action |
| --- | --- | --- |
| `GET /friendships/mutual/{userId}` | `getMutualFriends` | Get list of mutual friends with a user (enriched with user details) |
| `GET /friendships/mutual/{userId}/count` | `getMutualFriendsCount` | Get count of mutual friends (Neo4j query) |
| `GET /friendships/suggestions/graph` | `getGraphSuggestions` | Friend suggestions based on friends-of-friends (Neo4j traversal) |
| `GET /friendships/suggestions/contacts` | `getContactSuggestions` | Friend suggestions based on imported contacts (Neo4j `IN_CONTACT` score) |
| `GET /friendships/suggestions` | `getUnifiedSuggestions` | **Unified** suggestions with weighted scoring formula |

All suggestion endpoints accept `?page=0&size=20` (paginated).

#### 2.3 DTOs

**`ContactImportRequest`** (record):

| Field | Type | Validation |
| --- | --- | --- |
| `contacts` | `List<ContactEntry>` | `@NotNull`, `@Size(min=1, max=500)` |

**`ContactEntry`** (record): `{ name, phones: List<String>, emails: List<String> }`

**`ContactImportResponse`** (record):

| Field | Type |
| --- | --- |
| `totalContacts` | `int` |
| `normalizedPhones` | `int` |
| `normalizedEmails` | `int` |
| `matchedUsers` | `int` |
| `contactRelationsCreated` | `int` |
| `matchedUserIds` | `List<String>` |

**`FriendSuggestionResponse`** (record):

| Field | Type |
| --- | --- |
| `userId`, `fullName`, `avatar`, `phoneNumber` | `String` |
| `mutualFriendsCount` | `Integer` |
| `sharedGroupsCount` | `Integer` |
| `contactScore` | `Double` |
| `totalScore` | `Double` |

**`MutualFriendsResponse`** (record): `{ count: Integer, mutualFriends: List<FriendResponse> }`

#### 2.4 Contact Import Business Logic (`ContactServiceImpl`)

1. Extract phone numbers and emails from `ContactEntry` list
2. **Normalize phones** to E.164 format (Vietnam): strip non-digits, `0xx` → `+84xx`, `84xx` → `+84xx`
3. **Normalize emails** to lowercase/trimmed
4. **Search both E.164 and local formats** (sends `+84xxx` and `0xxx` variants to handle raw DB format)
5. Phone matching via `SearchServiceClient.findUsersByPhones()` (Feign → search-service → Elasticsearch `terms` query on `phoneNumber`)
6. Email matching via `UserServiceClient.findUsersByEmails()`
7. Deduplicate matched user IDs, remove self
8. **Create Neo4j graph**: ensure `User` node, create `IN_CONTACT` relationships
   - Phone matches: **score = 1.0**, source = `"PHONE"`
   - Email matches: **score = 0.8**, source = `"EMAIL"`
9. Return import summary

#### 2.5 Neo4j Graph Layer

**Node:** `@Node("User")` with `String id`

**Relationships:**
- `FRIEND` — bidirectional (existing)
- `IN_CONTACT` — directed, with `score`, `source`, `createdAt` (new)
- `IN_GROUP` — user → group (existing)

**Key Cypher Queries:**

| Query | Cypher Pattern |
| --- | --- |
| Mutual friends | `MATCH (a)-[:FRIEND]-(m)-[:FRIEND]-(b) WHERE a<>b RETURN DISTINCT m.id` |
| Graph suggestions (friends-of-friends) | `MATCH (u)-[:FRIEND]-(f)-[:FRIEND]-(suggest) WHERE NOT (u)-[:FRIEND]-(suggest) AND u<>suggest` → ordered by mutual count DESC |
| Contact suggestions | `MATCH (u)-[c:IN_CONTACT]->(suggest) WHERE NOT (u)-[:FRIEND]-(suggest)` → ordered by `c.score` DESC |
| **Unified suggestions** | `totalScore = mutualFriendsCount × 10 + sharedGroupsCount × 3 + contactScore × 5` — filtered: `WHERE mutualFriendsCount > 0 OR sharedGroupsCount > 0 OR contactScore > 0` |
| IN_CONTACT merge | `MERGE (a)-[c:IN_CONTACT]->(b) ON CREATE SET c.score=$score ON MATCH SET c.score = c.score + $score` |

#### 2.6 Search Service Integration

| Endpoint | Service | Action |
| --- | --- | --- |
| `POST /search/users/by-phones` | `search-service` | Elasticsearch `terms` query on `phoneNumber` field, filtered by `role=USER` |

Called via Feign client `SearchServiceClient` from `friend-service`.

#### 2.7 Mobile App Changes

| File / Component | Description |
| --- | --- |
| `useContactSync` hook | Uses `expo-contacts` to read device contacts, requests permission, **24-hour sync interval** (stored in `SecureStore`), normalizes phones (`0xx` → `+84xx`), batches in chunks of **500**, calls `POST /contacts/import` per batch, invalidates suggestion query cache after sync |
| `find-friends-contacts.tsx` | Main screen: auto-syncs on mount, shows contact suggestions, handles permission denied / empty states, pull-to-refresh, force sync |
| `contact-suggestion-item.tsx` | Renders suggestion with Add Friend / Cancel / Accepted states, checks friendship status per item |
| `useContactSuggestions` / `useGraphSuggestions` hooks | React Query hooks for suggestion endpoints |
| `settings/contacts/index.tsx` | Settings toggle for `syncContacts` |
| `settings/calls/index.tsx` | Call settings (allowVideoCalls, videoQuality SD/HD) |

#### 2.8 Web Frontend Changes (Suggestions only — no contact sync on web)

| File / Component | Description |
| --- | --- |
| `friend.api.ts` | API client: `getMutualFriends`, `getMutualFriendsCount`, `getUnifiedSuggestions`, `getGraphSuggestions`, `getContactSuggestions` |
| `fetchSuggestionsWithFallback` | Tries unified endpoint first; on failure falls back to parallel graph + contact requests, deduplicates by `userId` |
| `useMutualFriends` / `useMutualFriendsCount` hooks | React Query hooks for mutual friends |
| `friend-suggestion-card.tsx` | Shows source badges: phone contact (green), mutual friends (blue), shared groups (violet); displays "{count} mutual friends · {count} shared groups"; actions: Add Friend / Accept / Recall / Skip |

---

## How Has This Been Tested? (Manual)

### Feature 1: Video Call (Backend + Web)

1. Run backend locally (`message-service`, `socket-service`, `api-gateway`)
2. Test each API endpoint:

| Test Case | Expected |
| --- | --- |
| Initiate call to online user | ✅ CallSession created (RINGING), Redis BUSY set (60s TTL), push notification sent, WebSocket signal received by receiver |
| Initiate call to yourself | ❌ `CALL_SELF_NOT_ALLOWED` (5005) |
| Initiate call while already in a call | ❌ `CALL_ALREADY_IN_PROGRESS` (5004) |
| Initiate call to busy user | ❌ `CALL_USER_BUSY` (5001) |
| Initiate call to non-existent user | ❌ `CALL_USER_NOT_FOUND` (5002) |
| Accept call (as receiver) | ✅ Status → IN_PROGRESS, BUSY extended to 2h, `ACCEPTED` signal sent, video stream starts |
| Accept call (as non-receiver) | ❌ `UNAUTHORIZED` |
| Reject call | ✅ Status → REJECTED, BUSY cleared, `REJECTED` signal sent, system message "Cuộc gọi bị từ chối" saved |
| End ongoing call | ✅ Status → ENDED, BUSY cleared, `ENDED` signal sent, system message with duration saved |
| Cancel outgoing call (timeout) | ✅ Status → MISSED, BUSY cleared, `CANCELLED` signal sent, system message "Cuộc gọi nhỡ" saved |
| Get/refresh token | ✅ Returns valid RTC token for room |
| Call history in chat | ✅ Call messages rendered correctly with duration/missed/rejected labels |

### Feature 2: Sync Contacts + Friend Suggestions (Backend + App)

1. Run backend locally (`friend-service`, `search-service`, `user-service`)
2. Test each flow:

| Test Case | Expected |
| --- | --- |
| Import contacts (valid phones) | ✅ Phones normalized to E.164, matched users found via Elasticsearch, `IN_CONTACT` relationships created in Neo4j, import summary returned |
| Import contacts (empty list) | ❌ Validation error (`@Size(min=1)`) |
| Import contacts (>500) | ❌ Validation error (`@Size(max=500)`) |
| Import contacts (permission denied on mobile) | ✅ Permission denied UI shown, no API call |
| Auto-sync on app open (within 24h) | ✅ Skipped (cached last sync time in SecureStore) |
| Auto-sync on app open (after 24h) | ✅ Sync triggered automatically |
| Force sync (pull-to-refresh) | ✅ Sync triggered regardless of interval |
| Get contact suggestions | ✅ Returns non-friend users from `IN_CONTACT` graph, ordered by score DESC |
| Get graph suggestions (friends-of-friends) | ✅ Returns friends-of-friends not yet connected, ordered by mutual count DESC |
| Get unified suggestions | ✅ Returns weighted results: `totalScore = mutual×10 + groups×3 + contact×5` |
| Get mutual friends with user | ✅ Returns mutual friend list with user details |
| Get mutual friends count | ✅ Returns correct count from Neo4j |
| Suggestion card shows correct badges (web) | ✅ Phone contact (green), mutual friends (blue), shared groups (violet) |
| Add friend from suggestion | ✅ Friend request sent, suggestion state updates |

---

## Risk Level

- [ ] Low – safe logic change
- [x] Medium – affects business logic
- [ ] High – affects auth / data integrity

**Rationale:** Introduces new MongoDB collection (`call_sessions`), new Neo4j relationships (`IN_CONTACT`), Redis BUSY state management, and Kafka event-driven call signaling. Does not affect auth or break existing schema — all changes are additive and backward compatible.

---

## Checklist

- [ ] PR title contains `[BH-KEY]`
- [ ] Commit messages follow convention
- [x] Jira ticket is linked
- [x] Input validation handled (call: self-call prevention, busy checks, authorization; contacts: batch size limit, phone normalization)
- [x] Error cases handled (7 call error codes + validation errors + circuit breaker fallback)
- [x] Manual testing completed
- [x] Redis BUSY state correctly set/cleared across all call lifecycle transitions
- [x] Kafka events dispatched for call signals and push notifications
- [x] Neo4j `IN_CONTACT` relationships created with correct scoring (phone=1.0, email=0.8)
- [x] Unified suggestion scoring formula verified: `mutual×10 + groups×3 + contact×5`
- [x] Mobile contact sync respects 24h interval and 500-batch limit
