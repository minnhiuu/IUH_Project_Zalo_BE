package com.bondhub.notificationservices.dto.response.notification;

import com.bondhub.notificationservices.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationGroupResponse(
        String id,
        NotificationType type,
        String referenceId,
        String title,
        String body,
        List<String> actorIds,
        int actorCount,
        boolean isRead,
        LocalDateTime lastModifiedAt
) {}
