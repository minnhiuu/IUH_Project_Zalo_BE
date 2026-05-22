package com.bondhub.common.event.notification;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailNotificationEvent {
    String recipientEmail;
    String subject;
    String templateId;
    Map<String, Object> templateParams;
}
