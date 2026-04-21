package com.bondhub.common.event.notification;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.enums.SystemNotificationCategory;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SystemNotificationEvent {

    String recipientId;
    String actorId;
    NotificationType type;
    SystemNotificationCategory category;
    String referenceId;
    String title;
    String body;

    Map<String, Object> metadata;

    LocalDateTime occurredAt;
}
