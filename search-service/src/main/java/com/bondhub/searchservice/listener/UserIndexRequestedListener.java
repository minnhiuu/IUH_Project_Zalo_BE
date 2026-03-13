package com.bondhub.searchservice.listener;

import com.bondhub.common.event.user.UserIndexRequestedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import com.bondhub.searchservice.service.FailedEventService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserIndexRequestedListener {
    ElasticsearchOperations esOperations;
    com.bondhub.searchservice.config.ElasticsearchProperties esProperties;
    FailedEventService failedEventService;
    ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "#{kafkaTopicProperties.userEvents.indexRequested}",
            groupId = "search-service-indexer-group",
            concurrency = "3"
    )
    public void handleIndexRequest(
            @Payload UserIndexRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Processing index request: userId={}, partition={}, offset={}",
                event.userId(), partition, offset);

        try {
            UserIndex userIndex = convertToUserIndex(event);
            esOperations.save(userIndex, IndexCoordinates.of(esProperties.getUserAlias()));
            
            ack.acknowledge();
            log.info("Successfully indexed user: {}", event.userId());

        } catch (Exception e) {
            log.error("Failed to index user: userId={}", event.userId(), e);
            throw e;
        }
    }

    @DltHandler
    public void handleIndexRequestDLQ(
            @Payload UserIndexRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String dlqTopic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int dlqPartition,
            @Header(KafkaHeaders.OFFSET) long dlqOffset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] errorMsgBytes,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) byte[] stackTraceBytes,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic,
            @Header(value = "kafka_dlt-original-partition", required = false) Integer originalPartition,
            @Header(value = "kafka_dlt-original-offset", required = false) Long originalOffset,
            @Header(value = "retry_topic-attempts", required = false) byte[] attemptsBytes,
            Acknowledgment ack) {
        
        String errorMessage = errorMsgBytes != null ? new String(errorMsgBytes) : "Unknown error";
        String stackTrace = stackTraceBytes != null ? new String(stackTraceBytes) : "No stacktrace available";
        
        // Final topic info to save
        String finalTopic = (originalTopic != null) ? originalTopic : dlqTopic;
        int finalPartition = (originalPartition != null) ? originalPartition : dlqPartition;
        long finalOffset = (originalOffset != null) ? originalOffset : dlqOffset;
        
        // Parse attempts
        int retryCount = 0;
        if (attemptsBytes != null) {
            try {
                // Kafka headers for attempts are usually stored as 4-byte integers
                retryCount = java.nio.ByteBuffer.wrap(attemptsBytes).getInt();
            } catch (Exception ignored) {}
        }

        log.error("Index requested event moved to DLQ: userId={}, originalTopic={}, error={}", 
                event.userId(), finalTopic, errorMessage);
        
        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            failedEventService.logFailure(event.userId(), EventType.USER_INDEX_REQUESTED, finalTopic, finalPartition, finalOffset, payloadJson, errorMessage, stackTrace, retryCount);
        } catch (Exception ex) {
            log.error("Critical error while logging failure to MongoDB", ex);
        }

        ack.acknowledge();
    }

    private UserIndex convertToUserIndex(UserIndexRequestedEvent event) {
        String roleName = "USER";
        if (event.role() != null) {
            try {
                roleName = event.role().getName();
            } catch (Exception e) {
                log.warn("Failed to get role name, defaulting to USER: {}", e.getMessage());
            }
        }

        return UserIndex.builder()
                .id(event.userId())
                .fullName(event.fullName())
                .phoneNumber(event.phoneNumber())
                .accountId(event.accountId())
                .role(roleName)
                .avatar(event.avatar())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
