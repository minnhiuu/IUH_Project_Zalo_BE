package com.bondhub.notificationservices.service.notification;

import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.notification.NotificationResponse;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.mapper.NotificationMapper;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.repository.NotificationRepository;
import com.bondhub.notificationservices.strategy.content.NotificationContentStrategy;
import com.bondhub.notificationservices.strategy.content.factory.ContentStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    ContentStrategyFactory contentStrategyFactory;
    NotificationRepository notificationRepository;
    NotificationMapper notificationMapper;

    @Override
    public NotificationResponse createFriendRequestNotification(
            CreateFriendRequestNotificationRequest request) {

        log.info("Creating friend request notification for receiver={}",
                request.receiverId());

        NotificationContentStrategy strategy = contentStrategyFactory.get(NotificationType.FRIEND_REQUEST);

        Notification notification = strategy.build(request);

        Notification saved = notificationRepository.save(notification);

        return notificationMapper.toResponse(saved);
    }
}
