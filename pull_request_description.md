# Pull Request

## Jira Ticket

- [BH-197](https://tranngochuyen.atlassian.net/browse/BH-197)
- [BH-261](https://tranngochuyen.atlassian.net/browse/BH-261)
- Parent Epic: [BH-194](https://tranngochuyen.atlassian.net/browse/BH-194)

---

## Description

Backend implementation for join request system, member blocking, transfer ownership, and admin management APIs in `message-service`. This PR introduces the full join-request lifecycle (create → approve/reject/cancel), membership approval with join questions, block/unblock member from group, self-block from being added, transfer ownership as a standalone API, and admin candidate/list queries — along with a service-layer separation extracting `JoinRequestService` from `GroupConversationService`.

---

## Type of change

- [ ] Bug fix (fixes an existing issue)
- [x] New feature (adds new functionality)
- [x] Refactor (no behavior change)
- [ ] Config / Infrastructure change
- [ ] Other (please describe)

---

## What was changed?

### 1. Service-Layer Separation — JoinRequestService

Extracted all join-request and join-by-link logic from `GroupConversationServiceImpl` into a dedicated service:

| Layer          | File                       | Responsibility                                                                    |
| -------------- | -------------------------- | --------------------------------------------------------------------------------- |
| Interface      | `JoinRequestService`       | 7 method contracts for join request + join preview APIs                            |
| Implementation | `JoinRequestServiceImpl`   | Join-by-link (direct or approval), join preview, CRUD join requests, join question |
| Group Service  | `GroupConversationService`  | Retains group lifecycle + new block/unblock + transfer ownership + admin queries   |
| Shared Utility | `ConversationHelper`       | New helpers: `fetchActorInfo()`, `findGroupConversation()`, `broadcastAndRespond()`, fallback lastMessage logic |

### 2. Join Request APIs (7 endpoints)

| Endpoint                                                       | Method                | Action                                                                                      |
| -------------------------------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------- |
| `POST /conversations/join/{token}`                             | `joinByLink`          | Join group via invite link; creates pending request if `membershipApprovalEnabled` or `joinQuestion` is set |
| `GET /conversations/join/{token}/preview`                      | `getJoinPreview`      | Get group preview info before joining (now includes `isBlockedFromGroup`, `membershipApprovalEnabled`, `hasPendingRequest`, `joinQuestion`) |
| `PUT /conversations/{id}/join-question`                        | `updateJoinQuestion`  | Set/update join question (Owner/Admin only, requires `membershipApprovalEnabled`)            |
| `GET /conversations/{id}/join-requests`                        | `getJoinRequests`     | Get pending join requests for a group (Owner/Admin only, paginated, sorted by `createdAt`)   |
| `POST /conversations/{id}/join-requests/{requestId}/approve`   | `approveJoinRequest`  | Approve a pending join request → adds member to group + broadcasts system message            |
| `POST /conversations/{id}/join-requests/{requestId}/reject`    | `rejectJoinRequest`   | Reject a pending join request → sends restricted system message to requester only            |
| `DELETE /conversations/{id}/join-requests/me`                  | `cancelMyJoinRequest` | Cancel my own pending join request                                                          |

### 3. Block/Unblock Member APIs (4 endpoints)

| Endpoint                                              | Method                   | Action                                                                                       |
| ----------------------------------------------------- | ------------------------ | -------------------------------------------------------------------------------------------- |
| `POST /conversations/{id}/block/{targetUserId}`       | `blockMemberFromGroup`   | Block member from group → removes them (soft-delete) + adds to `blockedUserIds` + broadcasts `BLOCK_MEMBER` system message |
| `DELETE /conversations/{id}/block/{targetUserId}`      | `unblockMemberFromGroup` | Unblock member from group (Owner only) → removes from `blockedUserIds`                       |
| `GET /conversations/{id}/blocked-members`             | `getBlockedMembers`      | Get blocked members list (Owner/Admin only, paginated)                                       |
| `GET /conversations/{id}/block-candidates`            | `getBlockCandidates`     | Get active members with MEMBER role eligible for blocking (Owner/Admin only, searchable, paginated) |

### 4. Transfer Ownership & Admin Query APIs (3 endpoints)

| Endpoint                                                    | Method              | Action                                                                                    |
| ----------------------------------------------------------- | ------------------- | ----------------------------------------------------------------------------------------- |
| `PATCH /conversations/{id}/transfer-owner/{targetUserId}`   | `transferOwnership` | Transfer group ownership to another member (Owner only) → swaps roles, broadcasts `TRANSFER_OWNER` system message |
| `GET /conversations/{id}/group-admins`                      | `getGroupAdmins`    | Get group owner and admins (owner first, admins sorted by name ASC, paginated)            |
| `GET /conversations/{id}/admin-candidates`                  | `getAdminCandidates`| Get non-owner members for admin management (admins first then members, sorted by name ASC, searchable, paginated). Owner only |

### 5. leaveGroup Refactor — Request Body DTO

Consolidated `leaveGroup` params (`silent`, `transferTo`, `blockReJoin`) into a single `LeaveGroupRequest` record:

```java
public record LeaveGroupRequest(
        boolean silent,
        String transferTo,
        boolean blockReJoin
) {}
```

| Field        | Type      | Default | Purpose                                                                |
| ------------ | --------- | ------- | ---------------------------------------------------------------------- |
| `silent`     | `boolean` | `false` | Leave silently (only Owner/Admin see system message)                   |
| `transferTo` | `String`  | `null`  | Target user ID to transfer ownership before leaving (Owner only)       |
| `blockReJoin`| `boolean` | `false` | Self-block from being re-added to this group via link or direct invite |

When `blockReJoin` is `true`, the leaving member is added to `selfBlockedUserIds` on the `Conversation` document, preventing them from being re-added.

### 6. New Model: `JoinRequest`

```java
@Document(collection = "join_requests")
public class JoinRequest extends BaseModel {
    String id;
    String conversationId;    // indexed
    String userId;            // indexed
    JoinRequestStatus status; // PENDING, APPROVED, REJECTED, CANCELLED
    LocalDateTime processedAt;
    String processedBy;
    String joinAnswer;        // answer to join question (if configured)
}
```

**Compound indexes:**
- `(conversationId, status)` — for paginated pending requests query
- `(conversationId, userId, status)` — for duplicate-check and cancel-my-request

### 7. Model Changes

**`Conversation`** — 2 new fields:

| Field               | Type           | Purpose                                                     |
| ------------------- | -------------- | ----------------------------------------------------------- |
| `blockedUserIds`    | `Set<String>`  | Users blocked from rejoining the group (by Owner/Admin)     |
| `selfBlockedUserIds`| `Set<String>`  | Users who self-blocked from being re-added to this group    |

**`GroupSettings`** — 1 new field:

| Field          | Type     | Purpose                                     |
| -------------- | -------- | ------------------------------------------- |
| `joinQuestion` | `String` | Optional question shown before join request |

**`JoinGroupPreviewResponse`** — 4 new fields:

| Field                       | Type      | Purpose                                   |
| --------------------------- | --------- | ----------------------------------------- |
| `isBlockedFromGroup`        | `boolean` | Whether current user is blocked           |
| `membershipApprovalEnabled` | `boolean` | Whether approval mode is enabled          |
| `hasPendingRequest`         | `boolean` | Whether current user has pending request  |
| `joinQuestion`              | `String`  | Join question text (if configured)        |

**`ConversationResponse`** — 1 new field:

| Field                      | Type   | Purpose                                             |
| -------------------------- | ------ | --------------------------------------------------- |
| `pendingJoinRequestCount`  | `Long` | Count of pending join requests (for Owner/Admin UI) |

### 8. Business Rules & Validation

| Rule                                                              | Error Code                            |
| ----------------------------------------------------------------- | ------------------------------------- |
| Join request already pending for this user                        | `CHAT_JOIN_REQUEST_ALREADY_PENDING`   |
| Join request not found                                            | `CHAT_JOIN_REQUEST_NOT_FOUND`         |
| Join request already processed (not PENDING)                      | `CHAT_JOIN_REQUEST_ALREADY_PROCESSED` |
| Join question configured but no answer provided                   | `CHAT_JOIN_QUESTION_REQUIRED`         |
| Membership approval not enabled (for join question update)        | `CHAT_APPROVAL_NOT_ENABLED`          |
| User is blocked from this group                                   | `CHAT_USER_BLOCKED_FROM_GROUP`        |
| User already blocked from group                                   | `CHAT_USER_ALREADY_BLOCKED_FROM_GROUP`|
| User not blocked from group (unblock attempt)                     | `CHAT_USER_NOT_BLOCKED_FROM_GROUP`    |
| Cannot block the group owner                                      | `CHAT_CANNOT_BLOCK_OWNER`            |
| User has blocked you from adding them to group                    | `CHAT_BLOCKED_FROM_ADDING`           |
| You already blocked this user from adding you                     | `CHAT_GROUP_ADD_ALREADY_BLOCKED`     |
| You haven't blocked this user                                     | `CHAT_GROUP_ADD_NOT_BLOCKED`         |

**Block role constraints:** Admin can only block MEMBER-role users; Owner can block MEMBER and ADMIN.

### 9. System Message Broadcasting

New system action types and their broadcast behavior:

| Action                    | SystemActionType           | Recipients          | Metadata                                    |
| ------------------------- | -------------------------- | ------------------- | ------------------------------------------- |
| Join request created      | `JOIN_REQUEST_CREATED`     | Owner/Admin only    | _(empty)_                                   |
| Join request approved     | `JOIN_REQUEST_APPROVED`    | All members         | `targetIds`, `targetNames`, `targetAvatars` |
| Join request rejected     | `JOIN_REQUEST_REJECTED`    | Requester only      | _(empty)_                                   |
| Block member              | `BLOCK_MEMBER`             | All members         | `targetIds`, `targetName`, `targetAvatar`   |
| Blocked from joining      | `BLOCKED_FROM_JOINING`     | Blocked user only   | _(empty)_                                   |
| Self-blocked from joining | `SELF_BLOCKED_FROM_JOINING`| Self only           | _(empty)_                                   |

**Restricted system messages:** For `JOIN_REQUEST_CREATED`, `JOIN_REQUEST_REJECTED`, `BLOCKED_FROM_JOINING`, and `SELF_BLOCKED_FROM_JOINING`, the `visibleTo` field limits who sees the message. The `lastMessage` in the conversation sidebar uses a fallback mechanism: if the current user is not in `visibleTo`, the system queries the most recent visible message from MongoDB instead.

**Negative actions:** `BLOCK_MEMBER` is treated as a negative action (preserves existing `lastMessage.timestamp` — does not push conversation to top of list).

### 10. ConversationHelper Enhancements

| Helper                                          | Purpose                                                                   |
| ----------------------------------------------- | ------------------------------------------------------------------------- |
| `ActorInfo` record + `fetchActorInfo()`         | Reusable actor name/avatar lookup (used across Group + JoinRequest services) |
| `findGroupConversation()`                       | Find conversation + assert it's a group                                   |
| `broadcastAndRespond()`                         | Broadcast conversation update + build response in one call                |
| `findFallbackLastMessage()`                     | MongoDB query to find the most recent message visible to a given user when `lastMessage.visibleTo` excludes them |
| `resolveSenderName()`                           | Resolve sender name from cache or `actorName` metadata fallback           |
| `pendingJoinRequestCount` in response builder   | Auto-counts pending join requests for groups with approval enabled        |

---

## How Has This Been Tested? (Manual)

1. Run backend locally (`message-service`).
2. Test each API endpoint via Postman/FE:

| Test Case                                                    | Expected                                                                             |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------ |
| Join group via link (approval disabled)                      | ✅ Direct join, `JOIN_BY_LINK` system message broadcast                               |
| Join group via link (approval enabled, no question)          | ✅ Pending join request created, `JOIN_REQUEST_CREATED` sent to Owner/Admin only       |
| Join group via link (approval enabled, with question)        | ❌ `CHAT_JOIN_QUESTION_REQUIRED` if answer empty; ✅ request created with answer        |
| Join group while already pending                             | ❌ `CHAT_JOIN_REQUEST_ALREADY_PENDING`                                                |
| Join group while blocked                                     | ❌ `CHAT_USER_BLOCKED_FROM_GROUP`                                                     |
| Approve join request                                         | ✅ Member added, `JOIN_REQUEST_APPROVED` system message, request status → APPROVED     |
| Reject join request                                          | ✅ `JOIN_REQUEST_REJECTED` sent to requester only, request status → REJECTED           |
| Cancel my join request                                       | ✅ Request status → CANCELLED                                                         |
| Update join question (approval enabled)                      | ✅ Question saved in group settings                                                   |
| Update join question (approval disabled)                     | ❌ `CHAT_APPROVAL_NOT_ENABLED`                                                        |
| Block member (Owner blocks Admin)                            | ✅ Member soft-deleted, added to `blockedUserIds`, `BLOCK_MEMBER` system message       |
| Block member (Admin blocks Admin)                            | ❌ Admin can only block MEMBER role                                                   |
| Block member (try to block Owner)                            | ❌ `CHAT_CANNOT_BLOCK_OWNER`                                                          |
| Unblock member (Owner only)                                  | ✅ Removed from `blockedUserIds`                                                      |
| Get blocked members                                          | ✅ Paginated list of blocked users                                                    |
| Get block candidates                                         | ✅ Active MEMBER-role members, searchable                                             |
| Transfer ownership                                           | ✅ Roles swapped, `TRANSFER_OWNER` system message                                     |
| Get group admins                                             | ✅ Owner first, then admins sorted by name                                            |
| Get admin candidates                                         | ✅ Non-owner members, admins first then members, searchable                           |
| Leave group with `blockReJoin=true`                          | ✅ Member deactivated + added to `selfBlockedUserIds`, `SELF_BLOCKED_FROM_JOINING` sent |
| Add member who self-blocked                                  | ❌ `CHAT_BLOCKED_FROM_ADDING`                                                         |
| Join preview shows correct flags                             | ✅ `isBlockedFromGroup`, `hasPendingRequest`, `joinQuestion` all correct              |

---

## Risk Level

- [ ] Low – safe logic change
- [x] Medium – affects business logic
- [ ] High – affects auth / data integrity

**Rationale:** Introduces new collection (`join_requests`), new fields on `Conversation` (`blockedUserIds`, `selfBlockedUserIds`), and new service layer. Does not affect auth or break existing schema (all changes are additive and backward compatible).

---

## Checklist

- [x] PR title contains `[BH-KEY]`
- [x] Commit messages follow convention
- [x] Jira ticket is linked
- [x] Input validation handled (role checks, status checks, duplicate prevention, block constraints)
- [x] Error cases handled (12 new custom error codes)
- [x] Manual testing completed
- [x] Service separation: `JoinRequestService` isolated from `GroupConversationService`
- [x] System messages broadcast for all join request + block actions
- [x] Restricted system messages use `visibleTo` with fallback `lastMessage` logic
- [x] New MongoDB indexes for `join_requests` collection