package com.bondhub.notificationservices.service.notification;

import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.notification.NotificationResponse;

public interface NotificationService {

    NotificationResponse createFriendRequestNotification(CreateFriendRequestNotificationRequest request);
}
