package com.bondhub.notificationservices.model;

import com.bondhub.notificationservices.enums.BatchStatus;
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
        @CompoundIndex(
                name = "status_expires_idx",
                def = "{'status': 1, 'windowExpiresAt': 1}"
        )
})
public class NotificationBatch {

    @MongoId
    String id;

    @Indexed(unique = true)
    String batchKey;         // "{type}:{recipientId}"

    String recipientId;
    NotificationType type;
    LocalDateTime createdAt;
    LocalDateTime windowExpiresAt;  // = createdAt + windowSeconds

    @Indexed
    BatchStatus status;

}