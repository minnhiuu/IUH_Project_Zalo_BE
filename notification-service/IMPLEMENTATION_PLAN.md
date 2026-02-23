# Notification Batcher — Implementation Plan (BH-147)

Branch: `feature/09-firebase-notfication`  
HEAD hiện tại: `d4ecb00` — centralize notification orchestrator  
Format commit: `feat(notification-service): [BH-147] <mô tả>`

---

## ⚠️ KHÔNG XÓA — TÁI SỬ DỤNG

### Strategy/Factory pattern — **GIỮ, đổi output type**

| File                                                   | Hành động         | Lý do                                                          |
| ------------------------------------------------------ | ----------------- | -------------------------------------------------------------- |
| `strategy/content/NotificationContentStrategy.java`    | **Sửa interface** | `build()` trả về `RawNotificationEvent` thay vì `Notification` |
| `strategy/content/FriendRequestContentStrategy.java`   | **Sửa impl**      | Extract `actorId/recipientId/referenceId/payload` từ request   |
| `strategy/content/factory/ContentStrategyFactory.java` | **Giữ nguyên**    | Lookup theo type vẫn cần                                       |

**Tại sao cần giữ?**  
Mỗi `NotificationType` nhận một `request` có cấu trúc khác nhau:

- `CreateFriendRequestNotificationRequest` → có `senderId`, `receiverId`, `friendshipId`
- `PostLikeNotificationRequest` (tương lai) → có `actorId`, `postOwnerId`, `postId`

Strategy là nơi duy nhất biết cách map từng request shape → `RawNotificationEvent`. Không có gì thay thế được vai trò này.

```
Trước:  Strategy.build(request) → Notification  (title/body hardcode bên trong)
Sau:    Strategy.build(request) → RawNotificationEvent  (chỉ extract data, không render)
                                          ↓
                                   Kafka Queue 1
                                          ↓
                              BatchFlushService → TemplateEngine → render
```

### `Notification` model — **GIỮ NGUYÊN**

`InAppDeliveryHandler` (Commit 7) vẫn `.save()` vào collection `notifications`:

```
BatchedNotificationEvent → InAppDeliveryHandler → Notification.builder()... → save()
```

Đây là **in-app inbox document** — user mở app đọc từ đây. Không bỏ được.

> `TemplateEngine.java`, `NotificationTemplateService`, `NotificationTemplate` model — **GIỮ LẠI và mở rộng**.

---

## Tổng quan 7 commits

```
Commit 1 — Redis dep + config infra
Commit 2 — NotificationChannel enum + BatchWindowConfig enum + mở rộng NotificationType
Commit 3 — Thêm channel vào NotificationTemplate + cập nhật Repository & Service
Commit 4 — RawNotificationEvent + BatchedNotificationEvent DTOs
Commit 5 — MongoDB batch audit models + Repositories
Commit 6 — BatcherService + BatchScheduler + BatchFlushService (core batcher)
Commit 7 — Kafka Consumer + Pipeline Steps + Delivery Worker + refactor Orchestrator
```

---

## COMMIT 1 — Redis dep + config infra

**Message:** `feat(notification-service): [BH-147] add Redis dependency, RedisConfig, SchedulerConfig and infra properties`

### Tại sao commit này riêng?

Infrastructure phải đứng trước logic. Các commit sau phụ thuộc vào `StringRedisTemplate` bean và `TaskScheduler` bean. Nếu gộp vào thì khi test từng commit sẽ không compile được.

---

### 1.1 `pom.xml` — thêm dependency

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Giải thích:**

- `spring-boot-starter-data-redis` tự cấu hình `LettuceConnectionFactory` (Lettuce là Redis client async, thread-safe, khác với Jedis là blocking).
- Khi có dependency này + `spring.data.redis.*` properties → Spring Boot tự tạo `StringRedisTemplate` và `RedisTemplate<Object,Object>` bean mặc định.
- Ta sẽ tự định nghĩa lại để kiểm soát serializer.

---

### 1.2 `application.yml` — thêm properties

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost} # đọc từ env var, fallback localhost
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:} # rỗng nếu không set
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8 # max connection đang dùng đồng thời
          max-idle: 8 # max connection idle (giữ sẵn trong pool)
          min-idle: 0 # min connection idle

app:
  firebase:
    config-path: ${FIREBASE_CONFIG_PATH:} # thêm : để không bắt buộc set
  kafka:
    topics:
      raw-notifications: raw-notifications # Queue 1: sự kiện thô vào
      delivery-queue: notification-delivery # Queue 2: sau khi batch xong
  batch:
    recovery-on-startup: true # @PostConstruct quét OPEN batches khi restart
```

**Giải thích:**

- `${VAR:default}` — Spring EL expression: đọc env var `VAR`, nếu không có thì dùng `default`.
- `lettuce.pool` — connection pooling: tránh mở/đóng TCP connection mỗi request.
- Đặt topic name vào config thay vì hardcode trong code → dễ thay đổi mà không cần recompile.

---

### 1.3 `config/RedisConfig.java` — **tạo mới**

```java
package com.bondhub.notificationservices.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate: cả key và value đều là plain String (UTF-8).
     * Dùng cho BatcherService: SET NX, RPUSH, LRANGE — tất cả đều là String JSON.
     *
     * Tại sao không dùng RedisTemplate<String, Object>?
     * Vì khi RPUSH ta serialize thủ công thành JSON String,
     * khi LRANGE ta deserialize thủ công lại → tường minh hơn, tránh type mismatch.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
        // RedisConnectionFactory được Spring Boot tự tạo từ spring.data.redis.* properties
    }
}
```

**Kiến thức — StringRedisTemplate vs RedisTemplate:**
| | StringRedisTemplate | RedisTemplate\<K,V\> |
|---|---|---|
| Key serializer | StringRedisSerializer | JdkSerializationRedisSerializer (binary) |
| Value serializer | StringRedisSerializer | JdkSerializationRedisSerializer (binary) |
| Dùng cho | Text/JSON thuần | Object tùy ý |
| Dễ debug | ✅ (xem được trong redis-cli) | ❌ (binary blob) |

---

### 1.4 `config/SchedulerConfig.java` — **tạo mới**

```java
package com.bondhub.notificationservices.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig.java {

    /**
     * TaskScheduler cho phép schedule job động tại Instant bất kỳ.
     *
     * Tại sao không dùng @Scheduled?
     * @Scheduled chỉ hỗ trợ fixed-rate / fixed-delay / cron — tất cả đều static.
     * Ta cần: mỗi batch key có thời điểm flush riêng (Instant.now() + windowSeconds).
     * → Phải dùng TaskScheduler.schedule(Runnable, Instant).
     */
    @Bean
    public TaskScheduler batchTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);                          // 4 thread xử lý flush jobs song song
        scheduler.setThreadNamePrefix("batch-flush-");    // tên thread để dễ debug
        scheduler.setWaitForTasksToCompleteOnShutdown(true); // graceful shutdown
        scheduler.setAwaitTerminationSeconds(30);         // chờ tối đa 30s khi shutdown
        return scheduler;
    }
}
```

**Kiến thức — ThreadPoolTaskScheduler:**

- Mỗi lần gọi `scheduler.schedule(runnable, instant)` → thêm task vào thread pool.
- Thread pool size = 4 nghĩa là tối đa 4 batch flush chạy đồng thời.
- Nếu server đang xử lý 1000 batches, các task sẽ queue lại (không bị drop).

---

## COMMIT 2 — NotificationChannel + BatchWindowConfig + mở rộng NotificationType

**Message:** `feat(notification-service): [BH-147] add NotificationChannel enum, BatchWindowConfig enum, expand NotificationType`

### Tại sao commit này riêng?

Enums là foundation. Commit 3 (template), Commit 4 (events), Commit 6 (batcher) đều import từ đây. Tách ra để mỗi commit chỉ có 1 responsibility.

---

### 2.1 `enums/NotificationType.java` — **cập nhật**

```java
package com.bondhub.notificationservices.enums;

public enum NotificationType {
    FRIEND_REQUEST,   // A gửi lời mời kết bạn cho B
    POST_COMMENT,     // A bình luận bài của B
    POST_LIKE,        // A thích bài của B
    DOB,              // nhắc nhở sinh nhật
    MESSAGE_DIRECT,   // tin nhắn trực tiếp — KHÔNG batch
    CALL,             // cuộc gọi — KHÔNG batch
    SYSTEM            // thông báo hệ thống — KHÔNG batch
}
```

---

### 2.2 `enums/NotificationChannel.java` — **tạo mới**

```java
package com.bondhub.notificationservices.enums;

/**
 * Kênh gửi notification. Mỗi kênh có template riêng vì:
 * - IN_APP: có thể dài, dùng HTML hoặc markdown light
 * - FCM:    giới hạn ~240 chars cho body, cần ngắn gọn
 * - EMAIL:  full HTML, có thể chứa list, avatar, CTA button
 * - SMS:    giới hạn 160 ký tự, chỉ ASCII
 */
public enum NotificationChannel {
    IN_APP,  // hiển thị trong app (WebSocket/SSE + inbox)
    FCM,     // Firebase Cloud Messaging (push notification mobile)
    EMAIL,   // gửi qua Mailgun / SES / Sendgrid
    SMS      // gửi qua Sinch / Twilio
}
```

**Tại sao template phải khác nhau theo channel?**

Cùng event `FRIEND_REQUEST` nhưng render khác nhau:

```
IN_APP:  "Nguyễn Văn A và 4 người khác đã gửi lời mời kết bạn"
FCM:     "Bạn có 5 lời mời kết bạn mới"          (ngắn, FCM truncate nếu dài)
EMAIL:   "<h1>5 lời mời kết bạn</h1><p>Xem ai đã gửi...</p>"
SMS:     "5 friend requests on BondHub. Open app."  (160 chars, English)
```

---

### 2.3 `enums/BatchWindowConfig.java` — **tạo mới**

```java
package com.bondhub.notificationservices.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ánh xạ mỗi NotificationType với thời gian cửa sổ batch (giây).
 * windowSeconds = 0 → không batch, deliver ngay lập tức.
 */
@Getter
@RequiredArgsConstructor
public enum BatchWindowConfig {

    FRIEND_REQUEST(NotificationType.FRIEND_REQUEST, 10),  // 10s — khá urgent
    POST_COMMENT  (NotificationType.POST_COMMENT,    5),  //  5s — user muốn biết nhanh
    POST_LIKE     (NotificationType.POST_LIKE,       30), // 30s — ít urgent
    DOB           (NotificationType.DOB,             300),//  5 phút
    MESSAGE_DIRECT(NotificationType.MESSAGE_DIRECT,  0),  //  0 = không batch
    CALL          (NotificationType.CALL,            0),  //  0 = không batch
    SYSTEM        (NotificationType.SYSTEM,          0);  //  0 = không batch

    private final NotificationType type;
    private final int windowSeconds;

    /** Tiện ích: kiểm tra nhanh có cần batch không */
    public boolean isBatchable() {
        return windowSeconds > 0;
    }

    /**
     * Lookup theo NotificationType.
     * Dùng for-loop thay vì Map vì enum nhỏ (<10 phần tử), overhead không đáng kể.
     */
    public static BatchWindowConfig of(NotificationType type) {
        for (BatchWindowConfig cfg : values()) {
            if (cfg.type == type) return cfg;
        }
        throw new IllegalArgumentException("No BatchWindowConfig for: " + type);
    }
}
```

**Kiến thức — Enum với field và method:**

- Enum trong Java có thể có constructor, field, method — khác với C/C#.
- `@RequiredArgsConstructor` (Lombok) tự tạo constructor nhận (type, windowSeconds).
- `values()` trả về array tất cả enum constants — safe để iterate.

---

## COMMIT 3 — Thêm `channel` vào NotificationTemplate + cập nhật Repository & Service

**Message:** `feat(notification-service): [BH-147] add channel dimension to NotificationTemplate, update repository query and service render methods`

### Tại sao cần `channel`?

Hiện tại `NotificationTemplate` lookup bằng `(type, locale)` → 1 template cho tất cả channel. Nhưng FCM cần nội dung ngắn, email cần HTML, SMS cần ASCII 160 chars. Phải thêm `channel` để có 1 row riêng cho mỗi `(type, channel, locale)`.

---

### 3.1 `model/NotificationTemplate.java` — **cập nhật**

```java
package com.bondhub.notificationservices.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("notification_templates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
// Đảm bảo mỗi tổ hợp (type, channel, locale) là duy nhất
@CompoundIndexes({
    @CompoundIndex(
        name = "type_channel_locale_unique",
        def = "{'type': 1, 'channel': 1, 'locale': 1}",
        unique = true
    )
})
public class NotificationTemplate extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    NotificationType type;     // FRIEND_REQUEST, POST_LIKE, ...

    NotificationChannel channel; // IN_APP, FCM, EMAIL, SMS  ← THÊM MỚI

    String locale;             // "vi", "en", ...

    String titleTemplate;      // "{{firstName}} và {{othersCount}} người khác đã {{action}}"

    String bodyTemplate;       // "Bạn có {{count}} lời mời kết bạn mới"

    // XÓA field "language" — trùng ý nghĩa với locale, gây confuse
}
```

**⚠️ Lưu ý: xóa field `language`** — field này tồn tại trong code cũ nhưng trùng với `locale`. Nên bỏ để tránh data inconsistency.

**Migration tip:** Nếu đã có data trong MongoDB, cần chạy migration script thêm field `channel` vào document cũ.

---

### 3.2 `repository/NotificationTemplateRepository.java` — **cập nhật**

```java
package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.model.NotificationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationTemplateRepository
        extends MongoRepository<NotificationTemplate, String> {

    /**
     * Tìm template theo đúng 3 chiều: type + channel + locale, và chỉ active.
     *
     * Spring Data tự generate query từ method name:
     * findBy + Type + And + Channel + And + Locale + And + Active + True
     * → db.notification_templates.find({type:?, channel:?, locale:?, active:true})
     *
     * "Active" lấy từ field "active" trong BaseModel (đã có sẵn).
     */
    Optional<NotificationTemplate> findByTypeAndChannelAndLocaleAndActiveTrue(
            NotificationType type,
            NotificationChannel channel,
            String locale
    );
}
```

**Kiến thức — Spring Data method naming:**

- Spring Data tự parse tên method thành MongoDB query.
- `findBy` + `FieldName` (PascalCase) + `And/Or` + ...
- `ActiveTrue` → `{active: true}` (boolean shorthand).
- Không cần viết query thủ công → đơn giản, type-safe, dễ refactor.

---

### 3.3 `service/notificationtemplate/NotificationTemplateService.java` — **cập nhật interface**

```java
package com.bondhub.notificationservices.service.notificationtemplate;

import com.bondhub.notificationservices.dto.request.notificationtemplate.CreateTemplateRequest;
import com.bondhub.notificationservices.dto.request.notificationtemplate.UpdateTemplateRequest;
import com.bondhub.notificationservices.dto.response.notificationtemplate.NotificationTemplateResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.NotificationType;

import java.util.Map;

public interface NotificationTemplateService {

    NotificationTemplateResponse create(CreateTemplateRequest request);

    NotificationTemplateResponse update(String id, UpdateTemplateRequest request);

    NotificationTemplateResponse getTemplate(NotificationType type, NotificationChannel channel, String locale);

    /**
     * Render title template với data map.
     * @param channel kênh gửi — quyết định lấy template nào
     * @param data    biến thay thế: {"firstName":"A","othersCount":"4",...}
     */
    String renderTitle(NotificationType type, NotificationChannel channel, String locale, Map<String, Object> data);

    String renderBody(NotificationType type, NotificationChannel channel, String locale, Map<String, Object> data);
}
```

---

### 3.4 `service/notificationtemplate/NotificationTemplateServiceImpl.java` — **cập nhật**

```java
// Chỉ thay đổi các method có liên quan đến channel:

@Override
public NotificationTemplateResponse getTemplate(
        NotificationType type, NotificationChannel channel, String locale) {

    NotificationTemplate template = notificationTemplateRepository
        .findByTypeAndChannelAndLocaleAndActiveTrue(type, channel, locale)
        .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_TEMPLATE_NOT_FOUND));

    return notificationTemplateMapper.toResponse(template);
}

@Override
public String renderTitle(NotificationType type, NotificationChannel channel,
                           String locale, Map<String, Object> data) {
    NotificationTemplate template = notificationTemplateRepository
        .findByTypeAndChannelAndLocaleAndActiveTrue(type, channel, locale)
        .orElseThrow();

    // TemplateEngine.render() thay các {{biến}} bằng giá trị trong `data`
    return templateEngine.render(template.getTitleTemplate(), data);
}

@Override
public String renderBody(NotificationType type, NotificationChannel channel,
                          String locale, Map<String, Object> data) {
    NotificationTemplate template = notificationTemplateRepository
        .findByTypeAndChannelAndLocaleAndActiveTrue(type, channel, locale)
        .orElseThrow();

    return templateEngine.render(template.getBodyTemplate(), data);
}
```

**Ví dụ data map và template:**

```
Template (DB):   "{{firstName}} và {{othersCount}} người khác đã gửi lời mời kết bạn"
data map:        {"firstName": "Nguyễn Văn A", "othersCount": "4"}
Result:          "Nguyễn Văn A và 4 người khác đã gửi lời mời kết bạn"
```

### 3.5 DTOs cần cập nhật

`CreateTemplateRequest.java` cần thêm field `channel`:

```java
// record — thêm NotificationChannel channel
public record CreateTemplateRequest(
    NotificationType type,
    NotificationChannel channel,   // ← THÊM
    String locale,
    String titleTemplate,
    String bodyTemplate
) {}
```

---

## COMMIT 4 — RawNotificationEvent + BatchedNotificationEvent DTOs

**Message:** `feat(notification-service): [BH-147] add RawNotificationEvent and BatchedNotificationEvent as Kafka message DTOs`

### Tại sao cần 2 event riêng?

```
Queue 1 (raw-notifications):   RawNotificationEvent   — 1 event = 1 action của 1 actor
Queue 2 (notification-delivery): BatchedNotificationEvent — 1 event = N actions đã được gộp
```

Giữ 2 DTO riêng để 2 consumer (pre-delivery worker và delivery worker) nhận đúng kiểu.

---

### 4.1 `event/RawNotificationEvent.java` — **tạo mới** (package `event`)

```java
package com.bondhub.notificationservices.event;

import com.bondhub.notificationservices.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Sự kiện thô — 1 action của 1 actor.
 * Producer: các service khác (user-service, post-service) hoặc REST API của notification-service.
 * Consumer: RawNotificationConsumer (Kafka listener trên topic "raw-notifications").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RawNotificationEvent {

    String recipientId;   // ID user nhận notification
    String actorId;       // ID user thực hiện action
    NotificationType type;
    String referenceId;   // ID entity liên quan (friendshipId, postId...) — dùng cho dedup

    /**
     * Dữ liệu thêm tùy loại notification.
     * Ví dụ FRIEND_REQUEST: {"actorName": "A", "actorAvatar": "url..."}
     */
    Map<String, Object> payload;

    /**
     * Locale của recipient — quyết định ngôn ngữ template.
     * Default "vi" để không bắt buộc producer phải set.
     */
    @Builder.Default
    String locale = "vi";

    LocalDateTime occurredAt;  // thời điểm action xảy ra (không phải thời điểm gửi)
}
```

---

### 4.2 `event/BatchedNotificationEvent.java` — **tạo mới**

```java
package com.bondhub.notificationservices.event;

import com.bondhub.notificationservices.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sự kiện đã được gộp — output của BatchFlushService.
 * Producer: BatchFlushServiceImpl (sau khi cửa sổ batch đóng).
 * Consumer: DeliveryWorker (Kafka listener trên topic "notification-delivery").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BatchedNotificationEvent {

    String recipientId;
    NotificationType type;

    List<String> actorIds;     // tất cả actor IDs trong batch: [A, B, C, D, E]
    int actorCount;            // = actorIds.size()
    String firstActorId;       // actorIds.get(0) — để lấy avatar đại diện
    int othersCount;           // = actorCount - 1 → "A và {{othersCount}} người khác"
    String locale;             // locale của recipient

    // Các field này được render bởi BatchFlushService dùng NotificationTemplateService
    String renderedTitle;      // đã render, ready to use
    String renderedBody;

    /**
     * Toàn bộ raw events trong batch — Delivery Worker cần để:
     * - Build avatar list (5 avatar đầu tiên trong IN_APP)
     * - Build digest email HTML (list tên actor, link bài viết...)
     */
    List<Map<String, Object>> rawPayloads;

    LocalDateTime batchedAt;   // thời điểm batch được flush
}
```

---

## COMMIT 5 — MongoDB batch audit models + Repositories

**Message:** `feat(notification-service): [BH-147] add NotificationBatch and NotificationBatchItem MongoDB audit models with repositories`

### Tại sao cần MongoDB nếu đã có Redis?

Redis là **ephemeral** — data mất nếu server crash trong cửa sổ batch. MongoDB là **persistent audit trail**:

- Biết batch nào đang OPEN / đã FLUSHED
- Khi restart server: đọc MongoDB để re-schedule các batch OPEN chưa flush
- Analytics: bao nhiêu batch được flush mỗi ngày?

---

### 5.1 `model/NotificationBatch.java` — **tạo mới**

```java
package com.bondhub.notificationservices.model;

import com.bondhub.notificationservices.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Document("notification_batches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@CompoundIndexes({
    // Index để query: "tìm tất cả batch OPEN đã quá hạn" → dùng khi recovery
    @CompoundIndex(
        name = "status_expires_idx",
        def = "{'status': 1, 'windowExpiresAt': 1}"
    )
})
public class NotificationBatch {

    @MongoId
    String id;

    @Indexed(unique = true)
    String batchKey;         // "{type}:{recipientId}" — mirror Redis key, UNIQUE

    String recipientId;
    NotificationType type;
    LocalDateTime createdAt;
    LocalDateTime windowExpiresAt;  // = createdAt + windowSeconds

    @Indexed
    BatchStatus status;

    /**
     * OPEN:    batch đang trong cửa sổ, Redis list đang nhận events
     * FLUSHED: batch đã được flush và gửi đi
     * EXPIRED: cửa sổ đã qua nhưng không tìm thấy data để flush (hiếm)
     */
    public enum BatchStatus {
        OPEN, FLUSHED, EXPIRED
    }
}
```

---

### 5.2 `model/NotificationBatchItem.java` — **tạo mới**

```java
package com.bondhub.notificationservices.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Một raw event trong batch — ghi song song khi RPUSH vào Redis.
 * Mục đích: fallback source nếu Redis crash trước khi flush.
 */
@Document("notification_batch_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationBatchItem {

    @MongoId
    String id;

    @Indexed
    String batchKey;         // FK → NotificationBatch.batchKey

    String actorId;

    Map<String, Object> payload;  // raw event data

    LocalDateTime createdAt;
}
```

---

### 5.3 `repository/NotificationBatchRepository.java` — **tạo mới**

```java
package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.model.NotificationBatch;
import com.bondhub.notificationservices.model.NotificationBatch.BatchStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationBatchRepository
        extends MongoRepository<NotificationBatch, String> {

    Optional<NotificationBatch> findByBatchKey(String batchKey);

    /**
     * Dùng khi server restart để tìm các batch bị "stuck":
     * status = OPEN nhưng windowExpiresAt đã qua → cần re-flush ngay.
     */
    List<NotificationBatch> findByStatusAndWindowExpiresAtBefore(
            BatchStatus status,
            LocalDateTime cutoff
    );
}
```

---

### 5.4 `repository/NotificationBatchItemRepository.java` — **tạo mới**

```java
package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.model.NotificationBatchItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationBatchItemRepository
        extends MongoRepository<NotificationBatchItem, String> {

    List<NotificationBatchItem> findByBatchKey(String batchKey);

    void deleteByBatchKey(String batchKey);
}
```

---

## COMMIT 6 — BatcherService + BatchScheduler + BatchFlushService

**Message:** `feat(notification-service): [BH-147] implement BatcherService (Redis SET-NX/RPUSH), BatchScheduler, and BatchFlushService with template-based rendering`

### Đây là commit core nhất. Full flow:

```
event đến → BatcherService.buffer()
              └─ SET NX lock key (TTL=windowSeconds) → atomic check
              └─ RPUSH list key
              └─ nếu SET NX thắng: gọi BatchScheduler.scheduleFlush()
                    └─ TaskScheduler.schedule(runnable, Instant.now()+window)
                          └─ BatchFlushService.flush() sau window giây
                                └─ LRANGE + DEL (drain Redis)
                                └─ deserialize + aggregate actors
                                └─ renderTitle/renderBody qua NotificationTemplateService
                                └─ kafkaTemplate.send(deliveryTopic, BatchedNotificationEvent)
```

---

### 6.1 `batch/BatcherService.java` — **tạo mới** (interface)

```java
package com.bondhub.notificationservices.batch;

import com.bondhub.notificationservices.event.RawNotificationEvent;

public interface BatcherService {
    /**
     * Buffer sự kiện vào Redis batch window.
     * @return true nếu đây là sự kiện đầu tiên của batch (SET NX thắng)
     *         false nếu batch đã tồn tại (chỉ RPUSH thêm)
     *         false nếu type không cần batch (windowSeconds=0) → caller phải handle ngay
     */
    boolean buffer(RawNotificationEvent event);
}
```

---

### 6.2 `batch/BatcherServiceImpl.java` — **tạo mới**

```java
package com.bondhub.notificationservices.batch;

import com.bondhub.notificationservices.enums.BatchWindowConfig;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BatcherServiceImpl implements BatcherService {

    // Redis key convention (từ NOTIFICATION_BATCHER.md):
    // batch:lock:{type}:{recipientId} → SET với TTL = windowSeconds
    // batch:{type}:{recipientId}      → List<String> (JSON serialized events)
    static final String LOCK_PREFIX = "batch:lock:";
    static final String LIST_PREFIX = "batch:";

    StringRedisTemplate stringRedisTemplate;  // inject từ RedisConfig
    BatchScheduler batchScheduler;            // inject — forward reference ok vì Spring lazy-resolves

    // ObjectMapper để serialize RawNotificationEvent → JSON String
    // Khai báo ở đây thay vì inject để giữ stateless (ObjectMapper thread-safe sau configure)
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())           // support LocalDateTime
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO-8601 thay vì timestamp

    @Override
    public boolean buffer(RawNotificationEvent event) {
        BatchWindowConfig cfg = BatchWindowConfig.of(event.getType());

        // windowSeconds == 0 → type này không batch → trả về false để caller deliver ngay
        if (!cfg.isBatchable()) return false;

        String batchKey = buildBatchKey(event);  // "FRIEND_REQUEST:user_123"
        String lockKey  = LOCK_PREFIX + batchKey; // "batch:lock:FRIEND_REQUEST:user_123"
        String listKey  = LIST_PREFIX + batchKey; // "batch:FRIEND_REQUEST:user_123"

        // SET NX (SET if Not eXists) — ATOMIC operation:
        // Nếu key chưa tồn tại → set key="1" với TTL → trả về true
        // Nếu key đã tồn tại  → không làm gì         → trả về false
        // Tại sao atomic quan trọng: nếu 2 requests đến đồng thời, chỉ 1 "thắng" SET NX
        // Cả 2 đều có thể RPUSH → không mất event, nhưng chỉ 1 scheduleFlush được gọi
        Boolean isNewBatch = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(cfg.getWindowSeconds()));

        if (Boolean.TRUE.equals(isNewBatch)) {
            log.debug("New batch window opened: key={}, window={}s", batchKey, cfg.getWindowSeconds());
            // Schedule flush job chạy sau windowSeconds giây
            batchScheduler.scheduleFlush(batchKey, cfg.getWindowSeconds());
        }

        // RPUSH: thêm JSON event vào cuối list — cả trong batch cũ và mới
        String serialized = serialize(event);
        if (serialized != null) {
            stringRedisTemplate.opsForList().rightPush(listKey, serialized);
        }

        return Boolean.TRUE.equals(isNewBatch);
    }

    /**
     * Build batch key — static để BatchFlushServiceImpl có thể dùng lại.
     * Format: "{type}:{recipientId}" → đủ unique để phân biệt mỗi (user × type).
     */
    public static String buildBatchKey(RawNotificationEvent event) {
        return event.getType().name() + ":" + event.getRecipientId();
    }

    private String serialize(RawNotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event", e);
            return null;
        }
    }
}
```

---

### 6.3 `batch/BatchFlushService.java` — **tạo mới** (interface)

```java
package com.bondhub.notificationservices.batch;

public interface BatchFlushService {
    /**
     * Flush batch: drain Redis list → aggregate → render → publish to delivery queue.
     * Được gọi bởi BatchScheduler khi cửa sổ đóng.
     * @param batchKey "{type}:{recipientId}"
     */
    void flush(String batchKey);
}
```

---

### 6.4 `batch/BatchScheduler.java` — **tạo mới**

```java
package com.bondhub.notificationservices.batch;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BatchScheduler {

    TaskScheduler taskScheduler;          // bean từ SchedulerConfig
    BatchFlushService batchFlushService;  // inject bằng interface → loose coupling

    /**
     * Schedule 1 lần (one-shot) flush job tại Instant.now() + windowSeconds.
     *
     * TaskScheduler.schedule(Runnable, Instant):
     * - Không blocking — trả về ngay, task chạy trong thread pool sau đó
     * - Nếu windowSeconds = 0 → executeAt = now → chạy gần như ngay lập tức
     */
    public void scheduleFlush(String batchKey, int windowSeconds) {
        Instant executeAt = Instant.now().plusSeconds(windowSeconds);
        log.debug("Scheduling flush batchKey={} at {}", batchKey, executeAt);

        taskScheduler.schedule(
            () -> {
                try {
                    batchFlushService.flush(batchKey);
                } catch (Exception e) {
                    // Catch để không crash thread pool — log và tiếp tục
                    log.error("Flush failed for batchKey={}", batchKey, e);
                }
            },
            executeAt
        );
    }
}
```

---

### 6.5 `batch/BatchFlushServiceImpl.java` — **tạo mới**

```java
package com.bondhub.notificationservices.batch;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.bondhub.notificationservices.model.NotificationBatch;
import com.bondhub.notificationservices.model.NotificationBatch.BatchStatus;
import com.bondhub.notificationservices.repository.NotificationBatchRepository;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.bondhub.notificationservices.batch.BatcherServiceImpl.buildBatchKey;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BatchFlushServiceImpl implements BatchFlushService {

    static final String LOCK_PREFIX = "batch:lock:";
    static final String LIST_PREFIX = "batch:";

    StringRedisTemplate stringRedisTemplate;
    KafkaTemplate<String, Object> kafkaTemplate;          // bean có sẵn từ KafkaProducerConfig
    NotificationBatchRepository batchRepository;
    BatchScheduler batchScheduler;
    NotificationTemplateService templateService;           // tận dụng existing service ✓

    @Value("${app.kafka.topics.delivery-queue:notification-delivery}")
    String deliveryTopic;

    @Value("${app.batch.recovery-on-startup:true}")
    boolean recoveryOnStartup;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ----------------------------------------------------------------
    // Startup recovery — xử lý batch bị "stuck" khi server restart
    // ----------------------------------------------------------------

    /**
     * @PostConstruct chạy SAU khi bean được inject đầy đủ, TRƯỚC khi nhận request.
     * Tìm tất cả NotificationBatch có status=OPEN và windowExpiresAt đã qua → flush ngay.
     */
    @PostConstruct
    void recoverOpenBatches() {
        if (!recoveryOnStartup) return;

        List<NotificationBatch> stale = batchRepository
            .findByStatusAndWindowExpiresAtBefore(BatchStatus.OPEN, LocalDateTime.now());

        if (!stale.isEmpty()) {
            log.info("Recovering {} stale OPEN batches after restart", stale.size());
            stale.forEach(b -> batchScheduler.scheduleFlush(b.getBatchKey(), 0));
            // windowSeconds=0 → executes immediately
        }
    }

    // ----------------------------------------------------------------
    // Core flush
    // ----------------------------------------------------------------

    @Override
    public void flush(String batchKey) {
        String listKey = LIST_PREFIX + batchKey;
        String lockKey = LOCK_PREFIX + batchKey;

        // LRANGE 0 -1 → lấy toàn bộ list
        // DEL ngay sau → tránh race condition: nếu flush chạy 2 lần, lần 2 nhận empty list → no-op
        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        stringRedisTemplate.delete(listKey);
        stringRedisTemplate.delete(lockKey);

        if (rawList == null || rawList.isEmpty()) {
            log.debug("Flush no-op: empty list for batchKey={}", batchKey);
            return;
        }

        // Deserialize từng JSON String → RawNotificationEvent
        List<RawNotificationEvent> events = rawList.stream()
            .map(this::deserialize)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (events.isEmpty()) return;

        // Aggregate
        RawNotificationEvent first = events.get(0);
        List<String> actorIds = events.stream()
            .map(RawNotificationEvent::getActorId)
            .distinct()                    // loại trùng: A gửi 2 request → vẫn chỉ tính 1 actor
            .collect(Collectors.toList());

        int actorCount   = actorIds.size();
        String firstActor = actorIds.get(0);
        int othersCount  = actorCount - 1;
        String locale    = first.getLocale() != null ? first.getLocale() : "vi";

        // Build data map cho TemplateEngine
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("firstName",   firstActor);    // {{firstName}}
        templateData.put("othersCount", othersCount);   // {{othersCount}}
        templateData.put("count",       actorCount);    // {{count}}

        // Render title và body qua NotificationTemplateService + TemplateEngine (existing ✓)
        // Channel = IN_APP cho renderedTitle/Body — Delivery Worker sẽ re-render cho FCM/EMAIL/SMS
        String renderedTitle = renderSafe(first.getType(), NotificationChannel.IN_APP, locale, templateData, "title");
        String renderedBody  = renderSafe(first.getType(), NotificationChannel.IN_APP, locale, templateData, "body");

        // Build rawPayloads: dữ liệu thô cho FE điều khiển digest email hoặc avatar list
        List<Map<String, Object>> rawPayloads = events.stream().map(e -> {
            Map<String, Object> p = new HashMap<>(
                e.getPayload() != null ? e.getPayload() : Collections.emptyMap()
            );
            p.put("actorId",     e.getActorId());
            p.put("referenceId", e.getReferenceId());
            p.put("occurredAt",  e.getOccurredAt() != null ? e.getOccurredAt().toString() : null);
            return p;
        }).collect(Collectors.toList());

        BatchedNotificationEvent batched = BatchedNotificationEvent.builder()
            .recipientId(first.getRecipientId())
            .type(first.getType())
            .actorIds(actorIds)
            .actorCount(actorCount)
            .firstActorId(firstActor)
            .othersCount(othersCount)
            .locale(locale)
            .renderedTitle(renderedTitle)
            .renderedBody(renderedBody)
            .rawPayloads(rawPayloads)
            .batchedAt(LocalDateTime.now())
            .build();

        // Publish: key = recipientId → Kafka partition by recipient (mọi notification của 1 user vào cùng partition → ordered delivery)
        kafkaTemplate.send(deliveryTopic, first.getRecipientId(), batched);
        log.info("Batch flushed: key={}, actors={}, topic={}", batchKey, actorCount, deliveryTopic);

        // Update audit record
        batchRepository.findByBatchKey(batchKey).ifPresent(b -> {
            b.setStatus(BatchStatus.FLUSHED);
            batchRepository.save(b);
        });
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Render an toàn — nếu template chưa có trong DB thì fallback thay vì throw exception.
     */
    private String renderSafe(com.bondhub.notificationservices.enums.NotificationType type,
                               NotificationChannel channel, String locale,
                               Map<String, Object> data, String field) {
        try {
            return "title".equals(field)
                ? templateService.renderTitle(type, channel, locale, data)
                : templateService.renderBody(type, channel, locale, data);
        } catch (Exception e) {
            log.warn("Template not found for type={} channel={} locale={}, using fallback", type, channel, locale);
            // Fallback đơn giản — không làm crash flow
            return "title".equals(field)
                ? data.get("count") + " thông báo mới"
                : "";
        }
    }

    private RawNotificationEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, RawNotificationEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialize failed: {}", json, e);
            return null;
        }
    }
}
```

**Tại sao Kafka key = recipientId?**

- Kafka đảm bảo các message cùng key vào cùng partition.
- Cùng partition → consumer xử lý theo thứ tự.
- → Tất cả notification của User A được xử lý ordered, tránh race condition ghi vào inbox.

---

## COMMIT 7 — Kafka Consumer + Pipeline Steps + Delivery Worker + refactor Orchestrator

**Message:** `feat(notification-service): [BH-147] add Kafka consumer pipeline, delivery worker (InApp + FCM), refactor orchestrator to publish events`

### Cấu trúc package mới cần tạo:

```
kafka/
  RawNotificationConsumer.java     ← listen "raw-notifications" topic
pipeline/
  step/
    PipelineStep.java              ← interface
    UserValidatorStep.java
    NotificationBatcherStep.java
    UserPreferenceCheckerStep.java
delivery/
  DeliveryWorker.java              ← listen "notification-delivery" topic
  handler/
    InAppDeliveryHandler.java
    FcmDeliveryHandler.java
```

---

### 7.1 `pipeline/step/PipelineStep.java` — **tạo mới** (interface)

```java
package com.bondhub.notificationservices.pipeline.step;

import com.bondhub.notificationservices.event.RawNotificationEvent;

/**
 * Chain of Responsibility pattern — mỗi step xử lý 1 việc, có thể drop event.
 */
public interface PipelineStep {
    /**
     * @return true  → tiếp tục pipeline
     *         false → drop event (không gửi notification)
     */
    boolean process(RawNotificationEvent event);
}
```

**Kiến thức — Chain of Responsibility:**

- Mỗi step độc lập, dễ test, dễ thêm/bớt.
- Nếu UserValidatorStep trả về false → không cần chạy các step sau.
- Thứ tự: Validator → Batcher → PreferenceChecker (Batcher phải đứng sau Validator).

---

### 7.2 `pipeline/step/UserValidatorStep.java` — **tạo mới**

```java
package com.bondhub.notificationservices.pipeline.step;

import com.bondhub.notificationservices.event.RawNotificationEvent;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bước 1: Kiểm tra recipient có tồn tại và account active không.
 *
 * TODO: inject UserClient (OpenFeign) để gọi user-service check.
 * Hiện tại: pass-through (chấp nhận tất cả) cho đến khi user-service có endpoint.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserValidatorStep implements PipelineStep {

    // UserClient userClient;  ← TODO inject khi user-service có endpoint

    @Override
    public boolean process(RawNotificationEvent event) {
        if (event.getRecipientId() == null || event.getRecipientId().isBlank()) {
            log.warn("Drop event: missing recipientId, type={}", event.getType());
            return false;
        }
        // TODO: userClient.exists(event.getRecipientId()) → nếu false thì return false
        return true;
    }
}
```

---

### 7.3 `pipeline/step/NotificationBatcherStep.java` — **tạo mới**

```java
package com.bondhub.notificationservices.pipeline.step;

import com.bondhub.notificationservices.batch.BatcherService;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bước 2: Buffer event vào batch window hoặc pass-through nếu không batch.
 *
 * Logic:
 * - Nếu type cần batch (windowSeconds > 0) → buffer vào Redis → return FALSE
 *   (return false vì event đã được xử lý bởi batch window, không cần đi tiếp)
 * - Nếu type không batch (windowSeconds = 0) → return TRUE
 *   (đi tiếp pipeline để deliver ngay)
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationBatcherStep implements PipelineStep {

    BatcherService batcherService;

    @Override
    public boolean process(RawNotificationEvent event) {
        boolean isBatchable = batcherService.buffer(event);
        // buffer() trả về false nếu không batchable → tiếp tục deliver ngay
        // buffer() trả về true/false nếu batchable → luôn dừng pipeline (batch sẽ xử lý sau)
        boolean shouldContinue = !batcherService.isBatchableType(event.getType());
        return shouldContinue;
    }
}
```

> **Lưu ý:** Thêm method `isBatchableType(NotificationType)` vào `BatcherService` interface:
>
> ```java
> boolean isBatchableType(NotificationType type);
> // impl: return BatchWindowConfig.of(type).isBatchable();
> ```

---

### 7.4 `pipeline/step/UserPreferenceCheckerStep.java` — **tạo mới**

```java
package com.bondhub.notificationservices.pipeline.step;

import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.bondhub.notificationservices.service.preference.UserPreferenceService;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bước 3: Kiểm tra user có bật loại notification này không.
 * Dùng UserPreferenceService (local MongoDB copy của user settings).
 *
 * Nếu không tìm thấy preference → DEFAULT ALLOW (fail-open):
 * tránh tình huống user-service down mà notification bị drop.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserPreferenceCheckerStep implements PipelineStep {

    UserPreferenceService userPreferenceService;  // existing bean ✓

    @Override
    public boolean process(RawNotificationEvent event) {
        boolean allowed = userPreferenceService
            .isAllowed(event.getRecipientId(), event.getType());

        if (!allowed) {
            log.debug("Drop: user={} disabled type={}", event.getRecipientId(), event.getType());
        }
        return allowed;
    }
}
```

> **Cập nhật `UserPreferenceServiceImpl`** (hiện đang rỗng):
>
> ```java
> // Tạm thời: default allow tất cả
> public boolean isAllowed(String userId, NotificationType type) {
>     return true; // TODO: query MongoDB local preference copy
> }
> ```

---

### 7.5 `kafka/RawNotificationConsumer.java` — **tạo mới**

```java
package com.bondhub.notificationservices.kafka;

import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.bondhub.notificationservices.pipeline.step.NotificationBatcherStep;
import com.bondhub.notificationservices.pipeline.step.UserPreferenceCheckerStep;
import com.bondhub.notificationservices.pipeline.step.UserValidatorStep;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer lắng nghe Queue 1 (raw-notifications).
 * Chạy từng event qua pipeline: Validator → Batcher → PreferenceChecker.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RawNotificationConsumer {

    UserValidatorStep userValidatorStep;
    NotificationBatcherStep notificationBatcherStep;
    UserPreferenceCheckerStep userPreferenceCheckerStep;

    @KafkaListener(
        topics = "${app.kafka.topics.raw-notifications:raw-notifications}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}",
        containerFactory = "kafkaListenerContainerFactory"  // cần tạo KafkaConsumerConfig
    )
    public void consume(RawNotificationEvent event) {
        log.info("Received raw event: type={}, recipient={}", event.getType(), event.getRecipientId());

        // Pipeline: nếu bất kỳ step nào trả false → dừng
        if (!userValidatorStep.process(event)) return;
        if (!notificationBatcherStep.process(event)) return;   // thường dừng ở đây nếu batchable
        if (!userPreferenceCheckerStep.process(event)) return;

        // Nếu đến đây: type không batchable (CALL, MESSAGE_DIRECT, SYSTEM)
        // → TODO: deliver ngay không qua batch (gọi DeliveryWorker trực tiếp hoặc publish queue)
        log.debug("Non-batchable event passed pipeline, ready for immediate delivery: {}", event.getType());
    }
}
```

---

### 7.6 `delivery/strategy/DeliveryChannelStrategy.java` — **tạo mới** (interface)

Mirrors hệt `NotificationContentStrategy` — cùng pattern, khác chiều:

```
NotificationType  → ContentStrategyFactory     → NotificationContentStrategy.build()
NotificationChannel → DeliveryChannelStrategyFactory → DeliveryChannelStrategy.deliver()
```

```java
package com.bondhub.notificationservices.delivery.strategy;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;

/**
 * Mỗi impl xử lý việc gửi cho 1 channel cụ thể.
 * Thêm channel mới (SMS, EMAIL) = tạo class mới, không đụng DeliveryWorker.
 *
 * Pattern: Strategy — tách "what channel" khỏi "how to deliver".
 */
public interface DeliveryChannelStrategy {

    /** Channel mà strategy này xử lý — dùng để factory map */
    NotificationChannel supportedChannel();

    /**
     * Thực hiện gửi cho channel tương ứng.
     * @param event đã chứa renderedTitle/Body (IN_APP), rawPayloads, actorIds...
     *              Strategy tự re-render nếu cần template riêng cho channel mình.
     */
    void deliver(BatchedNotificationEvent event);
}
```

---

### 7.7 `delivery/strategy/InAppDeliveryStrategy.java` — **tạo mới**

```java
package com.bondhub.notificationservices.delivery.strategy;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.repository.NotificationRepository;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Lưu notification vào MongoDB in-app inbox + push realtime qua WebSocket.
 * Render lại với channel=IN_APP (có thể hiển thị nội dung dài hơn FCM).
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InAppDeliveryStrategy implements DeliveryChannelStrategy {

    NotificationRepository notificationRepository;   // existing ✓
    NotificationTemplateService templateService;      // existing ✓

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(BatchedNotificationEvent event) {
        // Build data map cho template engine
        Map<String, Object> data = buildTemplateData(event);
        String locale = event.getLocale() != null ? event.getLocale() : "vi";

        // renderedTitle trong event đã được render với IN_APP channel tại BatchFlushService
        // → có thể dùng lại trực tiếp, không cần re-render
        // Tuy nhiên nếu muốn tách bạch hoàn toàn, render lại ở đây:
        String title = renderSafe(event.getType(), NotificationChannel.IN_APP, locale, data, "title",
                                  event.getRenderedTitle());
        String body  = renderSafe(event.getType(), NotificationChannel.IN_APP, locale, data, "body",
                                  event.getRenderedBody());

        // Tận dụng Notification model có sẵn ✓
        Notification notification = Notification.builder()
            .userId(event.getRecipientId())
            .type(event.getType())
            .title(title)
            .body(body)
            .data(Map.of(
                "actorIds",    event.getActorIds(),
                "actorCount",  event.getActorCount(),
                "firstActorId", event.getFirstActorId(),
                "batchedAt",   event.getBatchedAt().toString()
            ))
            .isRead(false)
            .build();

        notificationRepository.save(notification);
        log.debug("IN_APP saved for user={}", event.getRecipientId());

        // TODO: push realtime nếu user online
        // websocketService.pushToUser(event.getRecipientId(), notification);
    }

    private Map<String, Object> buildTemplateData(BatchedNotificationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName",   event.getFirstActorId());  // {{firstName}}
        data.put("othersCount", event.getOthersCount());   // {{othersCount}}
        data.put("count",       event.getActorCount());    // {{count}}
        return data;
    }

    private String renderSafe(NotificationType type, NotificationChannel channel,
                               String locale, Map<String, Object> data,
                               String field, String fallback) {
        try {
            return "title".equals(field)
                ? templateService.renderTitle(type, channel, locale, data)
                : templateService.renderBody(type, channel, locale, data);
        } catch (Exception e) {
            log.warn("Template missing type={} channel={} locale={}, using fallback", type, channel, locale);
            return fallback;
        }
    }
}
```

---

### 7.8 `delivery/strategy/FcmDeliveryStrategy.java` — **tạo mới**

```java
package com.bondhub.notificationservices.delivery.strategy;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Gửi FCM push notification.
 * Re-render với channel=FCM — template FCM ngắn hơn, khác IN_APP.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FcmDeliveryStrategy implements DeliveryChannelStrategy {

    UserDeviceRepository userDeviceRepository;       // existing ✓
    NotificationTemplateService templateService;      // existing ✓

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.FCM;
    }

    @Override
    public void deliver(BatchedNotificationEvent event) {
        var devices = userDeviceRepository.findByUserId(event.getRecipientId());
        if (devices.isEmpty()) {
            log.debug("No FCM tokens for user={}", event.getRecipientId());
            return;
        }

        String locale = event.getLocale() != null ? event.getLocale() : "vi";
        Map<String, Object> data = buildTemplateData(event);

        // Re-render với template FCM riêng — ngắn gọn hơn IN_APP
        String title = renderSafe(event.getType(), locale, data, "title", event.getRenderedTitle());
        String body  = renderSafe(event.getType(), locale, data, "body",  event.getRenderedBody());

        for (var device : devices) {
            try {
                Message message = Message.builder()
                    .setToken(device.getFcmToken())
                    .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                    .putData("type",        event.getType().name())
                    .putData("recipientId", event.getRecipientId())
                    .putData("actorCount",  String.valueOf(event.getActorCount()))
                    .build();

                FirebaseMessaging.getInstance().send(message);  // existing FcmConfig ✓
                log.info("FCM sent user={} device={}", event.getRecipientId(), device.getFcmToken());
            } catch (Exception e) {
                log.error("FCM failed token={}", device.getFcmToken(), e);
            }
        }
    }

    private Map<String, Object> buildTemplateData(BatchedNotificationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName",   event.getFirstActorId());
        data.put("othersCount", event.getOthersCount());
        data.put("count",       event.getActorCount());
        return data;
    }

    private String renderSafe(NotificationType type, String locale,
                               Map<String, Object> data, String field, String fallback) {
        try {
            return "title".equals(field)
                ? templateService.renderTitle(type, NotificationChannel.FCM, locale, data)
                : templateService.renderBody(type, NotificationChannel.FCM, locale, data);
        } catch (Exception e) {
            log.warn("FCM template missing type={} locale={}", type, locale);
            return fallback;   // fallback: dùng lại IN_APP content đã render
        }
    }
}
```

---

### 7.9 `delivery/strategy/DeliveryChannelStrategyFactory.java` — **tạo mới**

Mirrors hệt `ContentStrategyFactory` — inject `List<DeliveryChannelStrategy>` Spring tự populate:

```java
package com.bondhub.notificationservices.delivery.strategy;

import com.bondhub.notificationservices.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory cho DeliveryChannelStrategy — cùng pattern với ContentStrategyFactory.
 *
 * Spring tự inject List<DeliveryChannelStrategy> chứa TẤT CẢ @Component impl:
 *   [InAppDeliveryStrategy, FcmDeliveryStrategy, ...]
 * → map thành Map<NotificationChannel, DeliveryChannelStrategy>
 *
 * Thêm channel mới (EmailDeliveryStrategy) = Spring tự discover, không cần sửa factory.
 */
@Component
@Slf4j
public class DeliveryChannelStrategyFactory {

    private final Map<NotificationChannel, DeliveryChannelStrategy> strategyMap;

    // Spring inject List tất cả bean implements DeliveryChannelStrategy
    public DeliveryChannelStrategyFactory(List<DeliveryChannelStrategy> strategies) {
        strategyMap = strategies.stream()
            .collect(Collectors.toMap(
                DeliveryChannelStrategy::supportedChannel,  // key = channel
                Function.identity()                          // value = strategy itself
            ));
        log.info("Loaded delivery strategies: {}", strategyMap.keySet());
    }

    /** Lấy strategy cho 1 channel cụ thể */
    public DeliveryChannelStrategy get(NotificationChannel channel) {
        DeliveryChannelStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new IllegalArgumentException("No delivery strategy for channel: " + channel);
        }
        return strategy;
    }

    /** Lấy tất cả strategies — DeliveryWorker dùng để fan-out */
    public List<DeliveryChannelStrategy> getAll() {
        return List.copyOf(strategyMap.values());
    }
}
```

---

### 7.10 `delivery/DeliveryWorker.java` — **tạo mới**

`DeliveryWorker` giờ **không biết** có bao nhiêu channel — chỉ iterate factory:

```java
package com.bondhub.notificationservices.delivery;

import com.bondhub.notificationservices.delivery.strategy.DeliveryChannelStrategyFactory;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer lắng nghe Queue 2 (notification-delivery).
 * Fan-out qua DeliveryChannelStrategyFactory — không hardcode channel.
 *
 * Thêm EmailDeliveryStrategy sau này → không đụng file này.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeliveryWorker {

    DeliveryChannelStrategyFactory deliveryFactory;

    @KafkaListener(
        topics = "${app.kafka.topics.delivery-queue:notification-delivery}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void deliver(BatchedNotificationEvent event) {
        log.info("Delivering type={} to user={}", event.getType(), event.getRecipientId());

        // Fan-out: gọi tất cả strategy đã đăng ký
        // TODO: lọc theo UserPreference — chỉ gọi channel user bật
        deliveryFactory.getAll().forEach(strategy -> {
            try {
                strategy.deliver(event);
            } catch (Exception e) {
                // Isolate failure: 1 channel fail không làm channel khác bị skip
                log.error("Delivery failed channel={} user={}",
                    strategy.supportedChannel(), event.getRecipientId(), e);
            }
        });
    }
}
```

**Khi thêm SMS sau:**

```java
@Component
public class SmsDeliveryStrategy implements DeliveryChannelStrategy {
    @Override public NotificationChannel supportedChannel() { return NotificationChannel.SMS; }
    @Override public void deliver(BatchedNotificationEvent event) { /* Twilio */ }
}
// Xong. DeliveryWorker, Factory không đổi gì.
```

---

### 7.11 `config/KafkaConsumerConfig.java` — **tạo mới**

```java
package com.bondhub.notificationservices.config;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notification-service}")
    String groupId;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bondhub.*");
        return props;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = baseConsumerProps();
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(2);  // 2 thread consumer song song
        return factory;
    }
}
```

---

### 7.10 Refactor `NotificationOrchestrator.java` — **cập nhật**

Thay vì `save()` trực tiếp → publish event lên Kafka Queue 1:

```java
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationOrchestrator {

    KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.raw-notifications:raw-notifications}")
    String rawTopic;

    /**
     * Thay vì save DB trực tiếp → publish lên Kafka.
     * Pipeline consumer sẽ xử lý: validate → batch → deliver.
     *
     * actorId và recipientId phải được truyền vào từ caller.
     */
    public void process(NotificationType type, String recipientId, String actorId,
                        String referenceId, Map<String, Object> payload) {

        RawNotificationEvent event = RawNotificationEvent.builder()
            .type(type)
            .recipientId(recipientId)
            .actorId(actorId)
            .referenceId(referenceId)
            .payload(payload)
            .occurredAt(LocalDateTime.now())
            .build();

        kafkaTemplate.send(rawTopic, recipientId, event);
        log.info("Published raw event: type={}, recipient={}", type, recipientId);
    }
}
```

### 7.11 Refactor Strategy pattern — **đổi output, không xóa**

**`strategy/content/NotificationContentStrategy.java`** — sửa interface:

```java
package com.bondhub.notificationservices.strategy.content;

import com.bondhub.notificationservices.event.RawNotificationEvent;

/**
 * Mỗi implementation biết cách extract dữ liệu từ 1 loại request
 * và đóng gói thành RawNotificationEvent để publish lên Kafka.
 *
 * Không còn render title/body ở đây — TemplateEngine đảm nhận sau.
 */
public interface NotificationContentStrategy {
    RawNotificationEvent build(Object request);
}
```

**`strategy/content/FriendRequestContentStrategy.java`** — sửa impl:

```java
package com.bondhub.notificationservices.strategy.content;

import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class FriendRequestContentStrategy implements NotificationContentStrategy {

    @Override
    public RawNotificationEvent build(Object rawRequest) {
        // Cast về đúng kiểu — ContentStrategyFactory đảm bảo đúng type được route vào đây
        var req = (CreateFriendRequestNotificationRequest) rawRequest;

        return RawNotificationEvent.builder()
            .type(NotificationType.FRIEND_REQUEST)
            .recipientId(req.getReceiverId())   // người nhận notification
            .actorId(req.getSenderId())          // người thực hiện action
            .referenceId(req.getFriendshipId())  // để dedup: 1 friendshipId → 1 notification
            .payload(Map.of(
                "senderName",   req.getSenderName(),   // {{firstName}} trong template
                "senderAvatar", req.getSenderAvatar()  // avatar hiển thị trong IN_APP
            ))
            .occurredAt(LocalDateTime.now())
            .build();
        // locale không set → dùng @Builder.Default = "vi"
        // Hoặc: lấy từ req.getLocale() nếu client gửi kèm
    }
}
```

**`ContentStrategyFactory`** — giữ nguyên, không cần sửa.

**`NotificationOrchestrator`** — thay `save()` bằng `kafkaTemplate.send()`:

```java
// process() cũ: strategy.build() → Notification → save()
// process() mới: strategy.build() → RawNotificationEvent → kafka.send()
public void process(NotificationType type, Object request) {
    NotificationContentStrategy strategy = contentStrategyFactory.get(type);
    RawNotificationEvent event = strategy.build(request);   // type-safe extraction
    kafkaTemplate.send(rawTopic, event.getRecipientId(), event);
    log.info("Published raw event: type={}, recipient={}", type, event.getRecipientId());
}
```

> `NotificationServiceImpl` không cần thay đổi — vẫn gọi `orchestrator.process(type, request)` như cũ.

---

## Tổng kết cấu trúc package sau 7 commits

```
com.bondhub.notificationservices/
├── batch/
│   ├── BatcherService.java              (commit 6)
│   ├── BatcherServiceImpl.java          (commit 6)
│   ├── BatchFlushService.java           (commit 6)
│   ├── BatchFlushServiceImpl.java       (commit 6)
│   └── BatchScheduler.java             (commit 6)
├── config/
│   ├── FcmConfig.java                   (existing)
│   ├── KafkaConsumerConfig.java         (commit 7)
│   ├── KafkaProducerConfig.java         (existing)
│   ├── OpenApiConfig.java               (existing)
│   ├── RedisConfig.java                 (commit 1)
│   ├── SchedulerConfig.java             (commit 1)
│   └── SecurityConfig.java             (existing)
├── delivery/
│   ├── DeliveryWorker.java              (commit 7) ← chỉ iterate factory, không biết channel
│   └── strategy/
│       ├── DeliveryChannelStrategy.java        (commit 7) ← interface, mirrors NotificationContentStrategy
│       ├── DeliveryChannelStrategyFactory.java  (commit 7) ← mirrors ContentStrategyFactory
│       ├── InAppDeliveryStrategy.java           (commit 7)
│       └── FcmDeliveryStrategy.java             (commit 7)
├── enums/
│   ├── BatchWindowConfig.java           (commit 2) ← trong enums ✓
│   ├── NotificationChannel.java         (commit 2) ← trong enums ✓
│   ├── NotificationType.java            (commit 2, updated)
│   └── Platform.java                   (existing)
├── event/
│   ├── BatchedNotificationEvent.java    (commit 4)
│   └── RawNotificationEvent.java        (commit 4)
├── kafka/
│   └── RawNotificationConsumer.java    (commit 7)
├── model/
│   ├── Notification.java               (existing)
│   ├── NotificationBatch.java          (commit 5)
│   ├── NotificationBatchItem.java      (commit 5)
│   ├── NotificationTemplate.java       (commit 3, updated + xóa language)
│   └── UserDevice.java                 (existing)
├── orchestrator/
│   └── NotificationOrchestrator.java   (commit 7, refactored)
├── pipeline/
│   └── step/
│       ├── PipelineStep.java            (commit 7)
│       ├── UserValidatorStep.java       (commit 7)
│       ├── NotificationBatcherStep.java  (commit 7)
│       └── UserPreferenceCheckerStep.java (commit 7)
├── repository/
│   ├── NotificationBatchItemRepository.java (commit 5)
│   ├── NotificationBatchRepository.java     (commit 5)
│   ├── NotificationRepository.java          (existing)
│   ├── NotificationTemplateRepository.java  (commit 3, updated)
│   └── UserDeviceRepository.java           (existing)
├── service/
│   ├── notificationtemplate/
│   │   ├── NotificationTemplateService.java     (commit 3, updated)
│   │   └── NotificationTemplateServiceImpl.java (commit 3, updated)
│   ├── preference/
│   │   ├── UserPreferenceService.java           (existing, add isAllowed())
│   │   └── UserPreferenceServiceImpl.java       (commit 7, implement isAllowed())
│   └── ... (other existing services)
├── utils/
│   └── TemplateEngine.java              (existing, không thay đổi) ✓
├── strategy/content/
│   ├── NotificationContentStrategy.java   (commit 7: đổi build() → RawNotificationEvent)
│   ├── FriendRequestContentStrategy.java  (commit 7: refactor, không xóa)
│   └── factory/ContentStrategyFactory.java (giữ nguyên, không sửa)
```

---

## Mẫu dữ liệu MongoDB `notification_templates` sau khi implement

```json
// FRIEND_REQUEST × IN_APP × vi
{
  "type": "FRIEND_REQUEST",
  "channel": "IN_APP",
  "locale": "vi",
  "titleTemplate": "{{firstName}} và {{othersCount}} người khác đã gửi lời mời kết bạn",
  "bodyTemplate": "Bạn có {{count}} lời mời kết bạn chờ xác nhận",
  "active": true
}

// FRIEND_REQUEST × FCM × vi
{
  "type": "FRIEND_REQUEST",
  "channel": "FCM",
  "locale": "vi",
  "titleTemplate": "Lời mời kết bạn mới",
  "bodyTemplate": "Bạn có {{count}} lời mời kết bạn mới",
  "active": true
}

// FRIEND_REQUEST × FCM × en
{
  "type": "FRIEND_REQUEST",
  "channel": "FCM",
  "locale": "en",
  "titleTemplate": "New friend requests",
  "bodyTemplate": "You have {{count}} new friend requests on BondHub",
  "active": true
}

// POST_LIKE × IN_APP × vi
{
  "type": "POST_LIKE",
  "channel": "IN_APP",
  "locale": "vi",
  "titleTemplate": "{{firstName}} và {{othersCount}} người khác đã thích bài viết của bạn",
  "bodyTemplate": "Bài viết của bạn nhận được {{count}} lượt thích",
  "active": true
}
```
