package com.bondhub.notificationservices.event;

import com.bondhub.notificationservices.enums.NotificationType;
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
    String firstActorId;
    String firstActorName;
    String firstActorAvatar;
    int othersCount;
    String locale;

    List<Map<String, Object>> rawPayloads;

    LocalDateTime batchedAt;
}