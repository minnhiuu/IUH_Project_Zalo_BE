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