package com.bondhub.notificationservices.service.notification;

import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.notificationservices.dto.request.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.NotificationResponse;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.mapper.NotificationMapper;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.repository.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationServiceImpl implements NotificationService {

    NotificationRepository notificationRepository;
    NotificationMapper notificationMapper;

    @Override
    public NotificationResponse createFriendRequestNotification(CreateFriendRequestNotificationRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", request.requestId());
        data.put("senderId", request.senderId());

        Notification notification = Notification.builder()
                .userId(request.receiverId())
                .type(NotificationType.FRIEND_REQUEST)
                .title("New Friend Request")
                .body(request.senderName() + " sent you a friend request")
                .data(data)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        return notificationMapper.toResponse(saved);
    }
}
