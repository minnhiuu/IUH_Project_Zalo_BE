package com.bondhub.notificationservices.service.delivery.strategy;

import com.bondhub.notificationservices.model.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InAppDeliveryStrategy implements NotificationStrategy {
    @Override
    public void execute(Notification notification) {
        // TODO: Gửi qua WebSocket/SSE để hiển thị real-time trên UI
        log.info("[InApp] Real-time signal sent for notification: {}", notification.getId());
    }
}
