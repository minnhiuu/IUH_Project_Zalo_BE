package com.bondhub.notificationservices.event;

import com.bondhub.common.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BatchedNotificationEvent {

    String recipientId;
    NotificationType type;

    List<String> actorIds;
    int actorCount;
    int totalEventCount;
    String referenceId;
    String lastActorId;
    String lastActorName;
    String lastActorAvatar;
    int othersCount;
    String locale;

    List<Map<String, Object>> rawPayloads;
    LocalDateTime lastOccurredAt;
    LocalDateTime batchedAt;
}