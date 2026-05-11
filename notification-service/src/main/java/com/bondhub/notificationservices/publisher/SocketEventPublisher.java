package com.bondhub.notificationservices.publisher;

import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.enums.SocketEventType;

import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SocketEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${kafka.topics.socket-events:socket.events}")
    String socketEventsTopic;

    public void publish(SocketEvent event) {
        try {
            kafkaTemplate.send(socketEventsTopic, event.targetUserId(), event);
            log.info("[Socket] Published socket event: type={}, recipient={}",
                    event.type(), event.targetUserId());
        } catch (Exception e) {
            log.error("[Socket] Failed to publish socket event: recipient={}",
                    event.targetUserId(), e);
        }
    }

    public void publishCleanup(String userId, String referenceId, NotificationType type) {
        Map<String, Object> cleanupData = new HashMap<>();
        cleanupData.put("action", "DELETE");
        cleanupData.put("referenceId", referenceId);
        cleanupData.put("type", type);

        SocketEvent event = new SocketEvent(
                SocketEventType.NOTIFICATION_CLEANUP,
                userId,
                "/queue/notifications",
                cleanupData
        );
        publish(event);
    }
}
