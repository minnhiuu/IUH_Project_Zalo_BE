package com.bondhub.notificationservices.service.notification;

import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.notification.NotificationAcceptedResponse;

public interface NotificationService {

    NotificationAcceptedResponse createFriendRequestNotification(CreateFriendRequestNotificationRequest request);
}
