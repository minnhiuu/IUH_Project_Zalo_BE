package com.bondhub.notificationservices.service.notification;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.notification.*;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationService {

    NotificationAcceptedResponse createFriendRequestNotification(CreateFriendRequestNotificationRequest request);

    NotificationHistoryResponse getNotificationHistory(
            LocalDateTime cursor,
            int limit
    );

    NotificationFlatHistoryResponse getUnreadHistory(LocalDateTime cursor, int limit);

    UserNotificationStateResponse getNotificationState();

    void markHistoryAsChecked();

    void markAsRead(String id);

    void markAllAsRead();

}
