package com.bondhub.notificationservices.publisher;

import com.bondhub.common.dto.client.socketservice.SocketEvent;
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
}
