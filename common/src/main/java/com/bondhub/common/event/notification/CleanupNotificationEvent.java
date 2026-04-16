package com.bondhub.common.event.notification;

import com.bondhub.common.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CleanupNotificationEvent {
    String recipientId;
    String referenceId;
    NotificationType type;
}
