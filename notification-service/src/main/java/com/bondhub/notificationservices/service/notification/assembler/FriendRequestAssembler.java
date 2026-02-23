package com.bondhub.notificationservices.service.notification.assembler;

import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendRequestAssembler implements NotificationAssembler {

    @Override
    public NotificationType getType() {
        return NotificationType.FRIEND_REQUEST;
    }

    @Override
    public RawNotificationEvent build(Object request) {
        CreateFriendRequestNotificationRequest req = (CreateFriendRequestNotificationRequest) request;

        return RawNotificationEvent.builder()
                .recipientId(req.receiverId())
                .actorId(req.senderId())
                .actorName(req.senderName())
                .actorAvatar(req.senderAvatar())
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(req.requestId())
                .payload(Map.of("requestId", req.requestId()))
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
