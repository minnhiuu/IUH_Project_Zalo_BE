package com.bondhub.socketservice.consumer;

import com.bondhub.common.dto.SocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

/**
 * Consumes SocketEvent messages from Kafka and forwards them to connected WebSocket clients.
 *
 * FANOUT PATTERN: Each socket-service instance has a unique Kafka group-id (random.uuid),
 * so ALL instances receive every event. Each instance then checks the local SimpUserRegistry
 * to see if the target user is connected HERE before pushing – preventing "ghost messages".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocketEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    @KafkaListener(
            topics = "${kafka.topics.socket-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(SocketEvent event) {
        if (event == null || event.targetUserId() == null || event.destination() == null) {
            log.warn("[Socket] Received null or incomplete SocketEvent – skipping");
            return;
        }

        // only push if the user is connected to THIS instance
        if (userRegistry.getUser(event.targetUserId()) == null) {
            log.debug("[Socket] User {} not connected to this instance – skipping event type={}",
                    event.targetUserId(), event.type());
            return;
        }

        log.info("[Socket] Pushing event type={} to user={} dest={}",
                event.type(), event.targetUserId(), event.destination());

        messagingTemplate.convertAndSendToUser(
                event.targetUserId(),
                event.destination(),
                event.payload()
        );
    }
}
