package com.bondhub.notificationservices.strategy.content;


import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendRequestContentStrategy implements NotificationContentStrategy {

    NotificationTemplateService templateService;

    @Override
    public NotificationType getType() {
        return NotificationType.FRIEND_REQUEST;
    }

    @Override
    public Notification build(Object request) {

        CreateFriendRequestNotificationRequest req = (CreateFriendRequestNotificationRequest) request;

        Map<String, Object> data = Map.of(
                "senderName", req.senderName(),
                "senderId", req.senderId(),
                "requestId", req.requestId()
        );

        String title = templateService.renderTitle(
                NotificationType.FRIEND_REQUEST,
                req.locale(),
                data
        );

        String body = templateService.renderBody(
                NotificationType.FRIEND_REQUEST,
                req.locale(),
                data
        );

        return Notification.builder()
                .userId(req.receiverId())
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(req.requestId())
                .title(title)
                .body(body)
                .data(data)
                .isRead(false)
                .build();
    }
}
