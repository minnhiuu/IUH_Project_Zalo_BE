package com.bondhub.notificationservices.dto.response.notification;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Set;

@Value
@Builder
public class UserNotificationStateResponse {
    long unreadCount;
    long uniqueActorCount;
    Set<String> unreadActorIds;
    LocalDateTime lastCheckedAt;
}
