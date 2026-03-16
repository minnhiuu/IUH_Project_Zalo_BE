package com.bondhub.notificationservices.dto.response.notification;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class UserNotificationStateResponse {
    long unreadCount;
    LocalDateTime lastCheckedAt;
}
