package com.bondhub.notificationservices.dto.response.notification;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record NotificationHistoryResponse(
        List<NotificationResponse> newest,
        List<NotificationResponse> today,
        List<NotificationResponse> previous,
        LocalDateTime nextCursor
) {
}
