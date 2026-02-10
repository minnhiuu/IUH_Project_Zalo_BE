package com.bondhub.userservice.listener;

import com.bondhub.common.event.user.UserIndexDeletedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.model.kafka.OutboxEvent;
import com.bondhub.common.repository.OutboxEventRepository;
import com.bondhub.userservice.config.ElasticsearchProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserIndexDeletedListener {
    ElasticsearchOperations esOperations;
    ElasticsearchProperties esProperties;
    OutboxEventRepository outboxEventRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "#{kafkaTopicProperties.userEvents.indexDeleted}",
            groupId = "user-search-indexer-group",
            concurrency = "3"
    )
    public void handleDeleteRequest(
            @Payload UserIndexDeletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Processing delete request: userId={}, partition={}, offset={}",
                event.userId(), partition, offset);

        try {
            esOperations.delete(event.userId(), IndexCoordinates.of(esProperties.getUserAlias()));
            updateOutboxStatus(event.userId(), EventType.USER_INDEX_DELETED, OutboxEvent.OutboxEventStatus.CONSUMED);
            ack.acknowledge();
            log.debug("Deleted user from index: {}", event.userId());

        } catch (Exception e) {
            log.error("Failed to delete user: userId={}", event.userId(), e);
            throw e;
        }
    }

    @DltHandler
    public void handleDeleteRequestDLQ(
            @Payload UserIndexDeletedEvent event,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Delete event in DLQ: userId={}, reason={}", event.userId(), exceptionMessage);
        updateOutboxStatus(event.userId(), EventType.USER_INDEX_DELETED, OutboxEvent.OutboxEventStatus.DEAD);
    }

    private void updateOutboxStatus(String userId, EventType eventType, OutboxEvent.OutboxEventStatus status) {
        try {
            Optional<OutboxEvent> outboxEventOpt = outboxEventRepository
                    .findTopByAggregateIdAndEventTypeOrderByCreatedAtDesc(userId, eventType);

            if (outboxEventOpt.isPresent()) {
                OutboxEvent outboxEvent = outboxEventOpt.get();
                outboxEvent.setStatus(status);
                outboxEventRepository.save(outboxEvent);
                log.info("✅ Updated outbox to {}: eventId={}, userId={}, eventType={}",
                        status, outboxEvent.getId(), userId, eventType);
            } else {
                log.warn("⚠️ No outbox event found: userId={}, eventType={}", userId, eventType);
            }
        } catch (Exception e) {
            log.error("❌ Failed to update outbox status: userId={}, eventType={}, status={}",
                    userId, eventType, status, e);
        }
    }
}
