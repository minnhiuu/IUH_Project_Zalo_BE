# Notification Batcher — System Design & Architecture

## 1. Full Pipeline Overview

```
REST API
  └─► Load Balancer
        └─► Send Notification API Service
              └─► Message Broker — Queue 1 (raw events)
                    │
                    └─► PRE-DELIVERY WORKER (sequential pipeline)
                          │
                          ├─ [1] User Validator
                          │       Kiểm tra recipient có tồn tại, tài khoản có active không
                          │
                          ├─ [2] Notification Batcher         ◄── Redis Cluster
                          │       Gộp nhiều events cùng loại thành 1 batch
                          │       theo time window (5s, 10s, 30s...)
                          │
                          ├─ [3] User Preference Checker      ◄── Redis (local pref copy)
                          │       Kiểm tra user có bật loại notification này không
                          │       → Drop nếu user đã tắt
                          │
                          └─ [4] Template Parser
                                  Chọn đúng template theo (type × channel × locale)
                                  render nội dung riêng cho từng channel:
                                    - In-App:  "A và 4 người khác đã gửi lời mời"
                                    - FCM:     "Bạn có 5 lời mời kết bạn mới"
                                    - Email:   Full HTML template
                                    - SMS:     "5 friend requests on BondHub"
                    │
                    └─► Message Broker — Queue 2 (delivery queue)
                          │
                          └─► DELIVERY WORKER (fan-out theo channel)
                                │
                                ├─ In-App Handler  → In-App API (WebSocket/SSE)
                                │                    → In-App Inbox Save (MongoDB)
                                │
                                ├─ Email Handler   → Mailgun / SES / Sendgrid
                                │
                                └─ SMS Handler     → Sinch / Twilio
                                │
                                └─► Log Aggregator
                                      → RDS/MySQL (audit log — immediate)
                                      → Cassandra (analytics — batched write)
```

---

## 2. Notification Batcher — Tại sao cần?

Không có batcher, mỗi event → 1 notification riêng:

```
A gửi friend request  →  push "A đã gửi lời mời"
B gửi friend request  →  push "B đã gửi lời mời"
C gửi friend request  →  push "C đã gửi lời mời"
... (20 push notifications làm phiền user)
```

Có batcher (window 10s):

```
A, B, C, D, E đều gửi trong 10 giây
→  1 push duy nhất: "A và 4 người khác đã gửi lời mời kết bạn"
```

---

## 3. Hai Approach: Batch on Write vs Batch on Read

### 3.1 Batch on Write

Gộp **trước khi lưu DB**, ngay lúc event đến.

```
Event đến → Check Redis → Gộp vào batch list → Job flush sau window → Deliver
```

- **Storage:** Redis (tạm thời, tự xóa sau window)
- **Phù hợp:** Push notification (FCM, SMS) — cần gửi 1 lần duy nhất
- **Latency:** Bắt buộc delay = window duration
- **Phức tạp:** Cao hơn (cần dynamic scheduler, atomic Redis ops)

### 3.2 Batch on Read

Lưu **tất cả** sự kiện vào DB ngay lập tức, gộp khi user **đọc** inbox.

```
Event đến → Lưu DB ngay → Khi user mở app, query GROUP BY (type, actorId.first, count)
```

- **Storage:** MongoDB (persistent, không mất data)
- **Phù hợp:** In-app inbox — hiển thị grouped khi user scroll
- **Latency:** Zero (lưu ngay)
- **Phức tạp:** Thấp hơn (chỉ cần aggregation query)

### So sánh

|                | Batch on Write                        | Batch on Read        |
| -------------- | ------------------------------------- | -------------------- |
| Thời điểm gộp  | Khi event đến                         | Khi user đọc         |
| Storage        | Redis (ephemeral)                     | MongoDB (persistent) |
| Mất data       | Có thể (nếu Redis crash trong window) | Không                |
| Phù hợp        | FCM push, SMS, Email                  | In-app inbox         |
| Latency        | Delay = window                        | Zero                 |
| Race condition | Cần xử lý cẩn thận                    | Không có             |

**→ Dùng cả hai:** Batch on Write cho push/FCM/SMS, Batch on Read cho in-app inbox.

---

## 4. Batch on Write — Chi tiết

### 4.1 Batching Key

```
batch_key = "{notification_type}:{recipient_id}"

// Ví dụ:
"FRIEND_REQUEST:user_123"
"POST_COMMENT:user_456"
"POST_LIKE:user_789"
```

Key này là **định danh duy nhất** của 1 batch đang mở cho 1 user + 1 loại notification.

### 4.2 Flow khi nhận event mới

```
New Notification Event
        │
        ▼
Generate batch_key = "{type}:{recipientId}"
        │
        ▼
Redis: EXISTS batch:lock:{batch_key} ?
        │
   ┌────┴────┐
  YES        NO
   │          │
   │          ▼
   │    SET NX batch:lock:{batch_key} EX {windowSeconds}
   │    Schedule job T + windowSeconds → batchFlush(batch_key)
   │          │
   └────┬─────┘
        │
        ▼
RPUSH batch:{batch_key}  <serialized event payload>
        │
        ▼
      DONE (event đã được buffer, chờ window đóng)
```

### 4.3 Atomic check-and-set (tránh race condition)

Nếu 2 events đến đồng thời, cả 2 đều thấy lock chưa tồn tại → tạo 2 batch → lỗi.

Giải pháp: `SET NX` (SET if Not eXists) là **atomic operation** trong Redis:

```java
// Chỉ 1 trong 2 concurrent threads "thắng" lệnh SET NX
Boolean isNewBatch = redisTemplate.opsForValue()
    .setIfAbsent(
        "batch:lock:" + batchKey,   // key
        "1",                         // value
        Duration.ofSeconds(windowSeconds)  // TTL = window duration
    );

if (Boolean.TRUE.equals(isNewBatch)) {
    // Thread này tạo batch mới → schedule flush job
    scheduleFlushJob(batchKey, windowSeconds);
}

// Cả 2 threads đều push vào list (bất kể ai thắng SET NX)
redisTemplate.opsForList().rightPush("batch:" + batchKey, serialize(payload));
```

### 4.4 Window Period — Dynamic theo từng type

Window period **khác nhau** cho mỗi `NotificationType`:

```java
public enum BatchWindowConfig {
    FRIEND_REQUEST(10),   // 10 giây — khá urgent
    POST_COMMENT(5),      //  5 giây — user muốn biết nhanh
    POST_LIKE(30),        // 30 giây — ít urgent, có thể gộp nhiều hơn
    DOB(300),             //  5 phút — batch theo ngày sinh cả ngày
    SYSTEM(0),            //  0 = không batch, gửi ngay
    MESSAGE_DIRECT(0),    //  0 = không batch, gửi ngay
    CALL(0);              //  0 = không batch, gửi ngay

    final int windowSeconds;
}
```

### 4.5 Dynamic Scheduler

Mỗi batch mới tạo ra **1 scheduled job riêng** với invocation time động:

```java
// Spring TaskScheduler (không phải @Scheduled — vì @Scheduled là static)
@Component
public class BatchScheduler {

    private final TaskScheduler taskScheduler;
    private final BatchFlushService batchFlushService;

    public void scheduleFlush(String batchKey, int windowSeconds) {
        Instant executeAt = Instant.now().plusSeconds(windowSeconds);

        taskScheduler.schedule(
            () -> batchFlushService.flush(batchKey),  // Runnable
            executeAt                                   // when to run
        );
    }
}
```

Lý do dùng `TaskScheduler` thay `@Scheduled`:

- `@Scheduled` là fixed-rate/fixed-delay — không thể schedule 1 job 1 lần tại thời điểm tùy ý
- `TaskScheduler.schedule(Runnable, Instant)` cho phép tạo job động tại bất kỳ thời điểm nào

---

## 5. Batch Job — Khi window đóng

### 5.1 Flow của Batch Job

```
Batch window finishes
        │
        ▼
Invoke batchFlush(batch_key)
        │
        ▼
LRANGE batch:{batch_key} 0 -1   →  [payload1, payload2, ..., payloadN]
        │
        ▼
DEL batch:{batch_key}            ← xóa ngay (atomic, tránh double-processing)
DEL batch:lock:{batch_key}
        │
        ▼
PROCESS:
  - count        = list.size()           // ví dụ: 50
  - actorIds     = [id1, id2, ..., idN]  // list tất cả actor
  - firstActor   = actorIds[0]           // để lấy avatar đại diện
  - othersCount  = count - 1             // "A và 49 người khác..."
  - rawPayloads  = list                  // toàn bộ raw data cho FE

  - Render content: "{{firstName}} và {{othersCount}} người khác đã {{action}}"
        │
        ▼
BatchedNotification {
    recipientId,
    type,
    actorIds,           // [A, B, C, ...]
    actorCount,         // 50
    firstActorId,       // A
    othersCount,        // 49
    renderedTitle,      // "A và 49 người khác đã thích bài viết của bạn"
    renderedBody,
    rawPayloads,        // ← toàn bộ raw events để FE build digest email
    batchedAt
}
        │
        ▼
Push → Kafka topic: "notification-delivery"
        │
        ▼
Delivery Worker xử lý tiếp
```

### 5.2 Tại sao gửi kèm rawPayloads?

FE nhận được `rawPayloads` (toàn bộ list events trong batch) có thể:

- Build **digest email**: "50 người đã thích bài viết — xem ai đã thích"
- Hiển thị **avatar list** trong in-app notification (5 avatar đầu tiên)
- Build **drill-down view**: click vào notification → xem danh sách đầy đủ

---

## 6. Data Models (MongoDB)

### `notification_batches` — Track batch đang tồn tại

```java
@Document("notification_batches")
public class NotificationBatch {
    @MongoId
    String id;

    @Indexed(unique = true)
    String batchKey;              // "FRIEND_REQUEST:user_123"

    String recipientId;
    NotificationType type;
    LocalDateTime createdAt;
    LocalDateTime windowExpiresAt;
    BatchStatus status;           // OPEN, FLUSHED, EXPIRED
}
```

### `notification_batch_items` — Từng event trong batch

```java
@Document("notification_batch_items")
public class NotificationBatchItem {
    @MongoId
    String id;

    String batchId;               // FK → notification_batches._id
    String actorId;
    Map<String, Object> payload;  // raw event data
    LocalDateTime createdAt;
}
```

> **Lưu ý:** Redis là source of truth trong window (dùng để flush), MongoDB là audit log sau khi batch đã xử lý xong.

---

## 7. Redis Key Structure

```
batch:lock:{type}:{recipientId}   →  String "1", TTL = windowSeconds
                                      EXISTS = batch đang mở
                                      NOT EXISTS = không có batch

batch:{type}:{recipientId}        →  List<SerializedPayload>
                                      RPUSH để thêm
                                      LRANGE + DEL để flush

freq:{userId}:{type}              →  Sorted Set (sliding window rate limit)
pref:{userId}                     →  Hash (user notification preferences cache)
```

---

## 8. Delivery Worker — Channel Fan-out

Sau khi Batch Job push vào delivery queue, **Delivery Worker** nhận `BatchedNotification` và fan-out:

```
BatchedNotification
        │
        ├─► [IN_APP]  InAppHandler
        │     → save vào MongoDB notifications (in-app inbox)
        │     → push qua WebSocket/SSE nếu user đang online
        │
        ├─► [PUSH]    FcmHandler
        │     → FirebaseMessaging.send(Message.builder()
        │           .setToken(fcmToken)
        │           .setNotification(Notification.builder()
        │               .setTitle(renderedTitle)
        │               .setBody(renderedBody).build())
        │           .build())
        │
        ├─► [EMAIL]   EmailHandler (future)
        │     → Mailgun / SES / Sendgrid
        │     → render full HTML template với rawPayloads (digest)
        │
        └─► [SMS]     SmsHandler (future)
              → Sinch / Twilio
              → short text chỉ 160 chars

        └─► Log Aggregator (async, tất cả channels)
              → ghi delivery attempt: {notificationId, channel, status, timestamp}
              → RDS: immediate write (audit)
              → Cassandra: batched write (analytics/reporting)
```

### Template per channel

`NotificationTemplate` cần thêm field `channel`:

```
(type=FRIEND_REQUEST, channel=IN_APP,  locale=vi) → "{{firstName}} và {{othersCount}} người khác đã gửi lời mời kết bạn"
(type=FRIEND_REQUEST, channel=FCM,     locale=vi) → "Bạn có {{count}} lời mời kết bạn mới"
(type=FRIEND_REQUEST, channel=EMAIL,   locale=vi) → "<h1>{{count}} lời mời kết bạn</h1>..."
(type=FRIEND_REQUEST, channel=SMS,     locale=en) → "{{count}} friend requests on BondHub"
```

---

## 9. Implementation Order

```
Phase 1 — Infrastructure
  [x] Redis config (StringRedisTemplate bean)
  [ ] Kafka producer config (pre-delivery → delivery queue)
  [ ] Kafka consumer config (raw events → pre-delivery pipeline)

Phase 2 — Batch on Write
  [ ] BatchWindowConfig enum (per-type window duration)
  [ ] BatcherService interface + impl (Redis SET NX + RPUSH)
  [ ] BatchScheduler (Spring TaskScheduler wrapper)
  [ ] BatchFlushJob (LRANGE + DEL + aggregate + push to delivery queue)
  [ ] NotificationBatch + NotificationBatchItem models + repositories

Phase 3 — Pre-delivery Pipeline (refactor Orchestrator)
  [ ] UserValidatorStep
  [ ] NotificationBatcherStep   (gọi BatcherService)
  [ ] UserPreferenceCheckerStep (gọi UserPreferenceServiceImpl)
  [ ] TemplateParserStep        (thêm channel dimension vào NotificationTemplate)

Phase 4 — Delivery Worker
  [ ] InAppHandler (save + WebSocket)
  [ ] FcmHandler   (FirebaseMessaging.send — đã config chưa dùng)
  [ ] EmailHandler (future)
  [ ] SmsHandler   (future)
  [ ] LogAggregator (async write)

Phase 5 — User Preference (Kafka event-driven local copy)
  [ ] UserNotificationPref model + repository
  [ ] UserSettingUpdatedConsumer (Kafka listener)
  [ ] UserPreferenceServiceImpl (query local MongoDB)
  [ ] user-service: publish UserSettingUpdatedEvent on settings change
```

---

## 10. Failure Scenarios & Mitigations

| Scenario                                      | Impact                             | Mitigation                                                                                         |
| --------------------------------------------- | ---------------------------------- | -------------------------------------------------------------------------------------------------- |
| Redis crash trong window                      | Batch events bị mất chưa kịp flush | Đồng thời ghi `notification_batch_items` vào MongoDB khi RPUSH → fallback khi Redis recover        |
| TaskScheduler job không chạy (server restart) | Batch bị "stuck"                   | @PostConstruct quét `notification_batches` có status=OPEN và `windowExpiresAt < now` → re-schedule |
| Kafka consumer fail                           | Delivery không xảy ra              | Kafka auto-retry + dead letter queue                                                               |
| Duplicate flush (job chạy 2 lần)              | Gửi 2 lần                          | Redis DEL atomic: lần 2 LRANGE trả về empty list → no-op                                           |
| user-service down                             | Preference check fail              | Default-allow: nếu không có local pref copy → cho phép gửi                                         |
