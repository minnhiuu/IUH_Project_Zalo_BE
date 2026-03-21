package com.bondhub.notificationservices.dto.response.notification;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationFlatHistoryResponse(
        List<NotificationResponse> items,
        LocalDateTime nextCursor
) {}
