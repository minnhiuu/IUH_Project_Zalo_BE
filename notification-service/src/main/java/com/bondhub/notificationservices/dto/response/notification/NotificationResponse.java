package com.bondhub.notificationservices.dto.response.notification;

import com.bondhub.notificationservices.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationResponse(
        String id,
        String userId,
        NotificationType type,
        String referenceId,
        String title,
        String body,
        boolean isRead
) {}
