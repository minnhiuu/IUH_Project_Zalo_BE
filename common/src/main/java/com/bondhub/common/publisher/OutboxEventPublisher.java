package com.bondhub.common.publisher;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.event.account.AccountRegisteredEvent;
import com.bondhub.common.event.user.UserIndexDeletedEvent;
import com.bondhub.common.event.user.UserIndexRequestedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.model.kafka.OutboxEvent;
import com.bondhub.common.repository.OutboxEventRepository;
import com.bondhub.common.event.user.UserCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;
    private final ObjectMapper objectMapper;

    /**
     * Save event to outbox table (transactional)
     */
    @Transactional
    public OutboxEvent saveToOutbox(String aggregateId, String aggregateType, EventType eventType, Object eventPayload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(eventPayload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .status(OutboxEvent.OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxEvent = outboxEventRepository.save(outboxEvent);
            log.info("✅ Event saved to outbox: eventType={}, aggregateId={}, id={}",
                    eventType, aggregateId, outboxEvent.getId());

            return outboxEvent;

        } catch (Exception e) {
            log.error("❌ Failed to save event to outbox: eventType={}, aggregateId={}",
                    eventType, aggregateId, e);
            throw new RuntimeException("Failed to save event to outbox", e);
        }
    }

    /**
     * Publish event to Kafka topic
     */
    public void publishToKafka(OutboxEvent outboxEvent) {
        try {
            outboxEvent.setStatus(OutboxEvent.OutboxEventStatus.PROCESSING);
            outboxEventRepository.save(outboxEvent);

            String topic = getTopicForEventType(outboxEvent.getEventType());

            // Deserialize to the appropriate event type based on EventType
            Class<?> eventClass = getEventClassForType(outboxEvent.getEventType());
            Object payload = objectMapper.readValue(outboxEvent.getPayload(), eventClass);

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    topic,
                    outboxEvent.getAggregateId(),
                    payload
            );

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    outboxEvent.setStatus(OutboxEvent.OutboxEventStatus.PUBLISHED);
                    outboxEvent.setProcessedAt(Instant.now());
                    outboxEventRepository.save(outboxEvent);
                    log.info("✅ Event published to Kafka: topic={}, eventId={}, partition={}, offset={}",
                            topic, outboxEvent.getId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    handlePublishFailure(outboxEvent, ex);
                }
            });

        } catch (Exception e) {
            handlePublishFailure(outboxEvent, e);
        }
    }

    /**
     * Save and immediately publish event (for immediate processing)
     */
    @Transactional
    public void saveAndPublish(String aggregateId, String aggregateType, EventType eventType, Object eventPayload) {
        OutboxEvent outboxEvent = saveToOutbox(aggregateId, aggregateType, eventType, eventPayload);
        publishToKafka(outboxEvent);
    }

    private void handlePublishFailure(OutboxEvent outboxEvent, Throwable ex) {
        outboxEvent.setStatus(OutboxEvent.OutboxEventStatus.FAILED);
        outboxEvent.setRetryCount(outboxEvent.getRetryCount() == null ? 1 : outboxEvent.getRetryCount() + 1);
        outboxEvent.setErrorMessage(ex.getMessage());
        outboxEventRepository.save(outboxEvent);
        log.error("❌ Failed to publish event to Kafka: eventId={}, retryCount={}",
                outboxEvent.getId(), outboxEvent.getRetryCount(), ex);
    }

    private String getTopicForEventType(EventType eventType) {
        return switch (eventType) {
            case ACCOUNT_REGISTERED -> kafkaTopicProperties.getAccountEvents().getRegistered();
            case ACCOUNT_UPDATED -> kafkaTopicProperties.getAccountEvents().getUpdated();
            case ACCOUNT_DELETED -> kafkaTopicProperties.getAccountEvents().getDeleted();
            case ACCOUNT_VERIFIED -> kafkaTopicProperties.getAccountEvents().getVerified();
            case ACCOUNT_ENABLED -> kafkaTopicProperties.getAccountEvents().getEnabled();
            case ACCOUNT_DISABLED -> kafkaTopicProperties.getAccountEvents().getDisabled();
            case USER_CREATED -> kafkaTopicProperties.getUserEvents().getCreated();
            case USER_UPDATED -> kafkaTopicProperties.getUserEvents().getUpdated();
            case USER_DELETED -> kafkaTopicProperties.getUserEvents().getDeleted();
            case USER_INDEX_REQUESTED -> kafkaTopicProperties.getUserEvents().getIndexRequested();
            case USER_INDEX_DELETED -> kafkaTopicProperties.getUserEvents().getIndexDeleted();
        };
    }

    private Class<?> getEventClassForType(EventType eventType) {
        return switch (eventType) {
            case ACCOUNT_REGISTERED, ACCOUNT_UPDATED, ACCOUNT_DELETED,
                 ACCOUNT_VERIFIED, ACCOUNT_ENABLED, ACCOUNT_DISABLED -> AccountRegisteredEvent.class;
            case USER_CREATED, USER_UPDATED, USER_DELETED -> UserCreatedEvent.class;
            case USER_INDEX_REQUESTED ->  UserIndexRequestedEvent.class;
            case USER_INDEX_DELETED -> UserIndexDeletedEvent.class;
        };
    }
}
