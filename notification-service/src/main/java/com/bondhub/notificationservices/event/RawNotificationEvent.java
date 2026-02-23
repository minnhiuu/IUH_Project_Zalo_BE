package com.bondhub.notificationservices.event;

import com.bondhub.notificationservices.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RawNotificationEvent {

    String recipientId;
    String actorId;
    NotificationType type;
    String referenceId;

    Map<String, Object> payload;

    @Builder.Default
    String locale = "vi";

    LocalDateTime occurredAt;
}