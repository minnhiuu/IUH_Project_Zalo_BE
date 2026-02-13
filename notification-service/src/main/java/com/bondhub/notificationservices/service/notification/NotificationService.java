package com.bondhub.notificationservices.service.notification;

import com.bondhub.notificationservices.dto.request.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.NotificationResponse;

public interface NotificationService {

    NotificationResponse createFriendRequestNotification(CreateFriendRequestNotificationRequest request);
}
