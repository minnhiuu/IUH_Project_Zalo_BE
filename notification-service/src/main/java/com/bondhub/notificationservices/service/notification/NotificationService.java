package com.bondhub.notificationservices.service.notification;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.notification.NotificationAcceptedResponse;
import com.bondhub.notificationservices.dto.response.notification.NotificationGroupResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {

    NotificationAcceptedResponse createFriendRequestNotification(CreateFriendRequestNotificationRequest request);

    PageResponse<List<NotificationGroupResponse>> getMyNotifications(Pageable pageable);
}
