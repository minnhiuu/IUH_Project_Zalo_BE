package com.bondhub.notificationservices.service.notification;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.dto.response.notification.*;

import java.time.LocalDateTime;

public interface NotificationService {

    NotificationHistoryResponse getNotificationHistory(
            LocalDateTime cursor,
            int limit
    );

    NotificationFlatHistoryResponse getUnreadHistory(LocalDateTime cursor, int limit);

    UserNotificationStateResponse getNotificationState();

    void markHistoryAsChecked();

    void markAsRead(String id);

    void markAllAsRead();

    void deactivateByReferenceIdAndType(String userId, String referenceId, NotificationType type);

    void sendTestNotification();
}
