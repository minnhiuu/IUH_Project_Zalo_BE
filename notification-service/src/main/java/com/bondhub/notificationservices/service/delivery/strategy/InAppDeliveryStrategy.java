package com.bondhub.notificationservices.service.delivery.strategy;

import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.dto.response.notification.NotificationResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.mapper.NotificationMapper;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.publisher.SocketEventPublisher;
import com.bondhub.notificationservices.service.delivery.NotificationStrategyHelper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InAppDeliveryStrategy implements NotificationStrategy {

    SocketEventPublisher socketEventPublisher;
    NotificationStrategyHelper strategyHelper;
    NotificationMapper notificationMapper;

    @Override
    public void execute(Notification persisted) {
        // MESSAGE_DIRECT and CALL have their own dedicated WebSocket channels
        // (/queue/messages, /queue/call-signals). Skip to avoid duplicate realtime notifications.
        if (persisted.getType() == NotificationType.MESSAGE_DIRECT ||
            persisted.getType() == NotificationType.MESSAGE_GROUP ||
            persisted.getType() == NotificationType.CALL) {
            log.debug("[InApp] Skipping realtime for direct-channel type: {}", persisted.getType());
            return;
        }

        String recipientId = persisted.getUserId();
        log.info("[InApp] Preparing real-time signal for notification: {} for user: {}", persisted.getId(), recipientId);

        try {
            // 1. Render content using the shared renderer
            var rendered = strategyHelper.render(persisted, NotificationChannel.IN_APP, null);

            // 2. Map to Response DTO
            NotificationResponse response = notificationMapper.toResponse(persisted, rendered.title(), rendered.body());

            // 3. Create Socket Event
            SocketEvent socketEvent = new SocketEvent(
                    SocketEventType.NOTIFICATION,
                    recipientId,
                    "/queue/notifications",
                    response
            );

            // 4. Publish to Kafka
            socketEventPublisher.publish(socketEvent);

            log.info("[InApp] Real-time signal sent for notification: {} to user: {}", 
                    persisted.getId(), recipientId);
            
        } catch (Exception e) {
            log.error("[InApp] Failed to deliver real-time notification to user {}: {}", recipientId, e.getMessage());
        }
    }
}
