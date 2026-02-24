package com.bondhub.notificationservices.dto.response;

import com.bondhub.notificationservices.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationResponse(
        String id,
        String userId,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> data,
        boolean isRead,
        LocalDateTime createdAt
) {}
