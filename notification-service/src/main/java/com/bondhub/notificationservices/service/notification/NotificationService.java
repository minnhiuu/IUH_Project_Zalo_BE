package com.bondhub.notificationservices.service.notification;

import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;

public interface NotificationService {

    void createFriendRequestNotification(CreateFriendRequestNotificationRequest request);
}
