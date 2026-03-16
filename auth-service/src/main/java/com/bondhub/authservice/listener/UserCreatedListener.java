package com.bondhub.authservice.listener;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.model.kafka.OutboxEvent;
import com.bondhub.common.repository.OutboxEventRepository;
import com.bondhub.common.event.user.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreatedListener {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTopicProperties kafkaTopicProperties;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.userEvents.created}",
            groupId = "auth-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserCreated(
            @Payload UserCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("📥 Received USER_CREATED event from Kafka: topic={}, partition={}, offset={}, userId={}, accountId={}",
                topic, partition, offset, event.getUserId(), event.getAccountId());

        try {
            // Find the original ACCOUNT_REGISTERED outbox event by accountId
            Optional<OutboxEvent> outboxEventOpt = outboxEventRepository
                    .findTopByAggregateIdAndEventTypeOrderByCreatedAtDesc(
                            event.getAccountId(), 
                            EventType.ACCOUNT_REGISTERED
                    );

            if (outboxEventOpt.isPresent()) {
                OutboxEvent outboxEvent = outboxEventOpt.get();
                outboxEvent.setStatus(OutboxEvent.OutboxEventStatus.CONSUMED);
                outboxEventRepository.save(outboxEvent);
                
                log.info("✅ Updated outbox event to CONSUMED: eventId={}, accountId={}, userId={}", 
                        outboxEvent.getId(), event.getAccountId(), event.getUserId());
            } else {
                log.warn("⚠️ No ACCOUNT_REGISTERED outbox event found for accountId: {}", event.getAccountId());
            }

            // Manual acknowledgment
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.debug("✅ Message acknowledged: offset={}", offset);
            }

        } catch (Exception e) {
            log.error("❌ Failed to update outbox event for accountId: {}, error: {}", 
                    event.getAccountId(), e.getMessage(), e);
            
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to process user created event", e);
        }
    }
}
