package com.bondhub.notificationservices.dto.response.notification;

import com.bondhub.common.enums.NotificationType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Builder
public record NotificationResponse(
        String id,
        NotificationType type,
        String referenceId,
        String title,
        String body,
        List<String> actorIds,
        int actorCount,
        boolean read,
        LocalDateTime lastModifiedAt,
        Map<String, Object> payload
) {}
