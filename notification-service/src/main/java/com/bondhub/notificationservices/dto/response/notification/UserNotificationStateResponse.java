package com.bondhub.notificationservices.dto.response.notification;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserNotificationStateResponse {
    long unreadCount;
    long notificationUnreadCount;
    long chatUnreadConversationCount;
    long notificationBadgeCount;
}
