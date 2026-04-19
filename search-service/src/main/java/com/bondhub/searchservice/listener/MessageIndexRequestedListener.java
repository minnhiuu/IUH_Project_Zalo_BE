package com.bondhub.searchservice.listener;

import com.bondhub.common.event.message.MessageIndexRequestedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.model.elasticsearch.MessageIndex;
import com.bondhub.searchservice.service.FailedEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageIndexRequestedListener {

    ElasticsearchOperations esOperations;
    ElasticsearchProperties esProperties;
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
            topics = "#{kafkaTopicProperties.messageEvents.indexRequested}",
            groupId = "search-service-message-indexer-group",
            concurrency = "3"
    )
    public void handleIndexRequest(
            @Payload MessageIndexRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Processing message index request: messageId={}, partition={}, offset={}",
                event.messageId(), partition, offset);

        try {
            MessageIndex doc = toMessageIndex(event);
            esOperations.save(doc, IndexCoordinates.of(esProperties.getMessageAlias()));
            ack.acknowledge();
            log.info("Successfully indexed message: {}", event.messageId());

        } catch (Exception e) {
            log.error("Failed to index message: messageId={}", event.messageId(), e);
            throw e;
        }
    }

    @DltHandler
    public void handleIndexRequestDLQ(
            @Payload MessageIndexRequestedEvent event,
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

        String finalTopic = (originalTopic != null) ? originalTopic : dlqTopic;
        int finalPartition = (originalPartition != null) ? originalPartition : dlqPartition;
        long finalOffset = (originalOffset != null) ? originalOffset : dlqOffset;

        int retryCount = 0;
        if (attemptsBytes != null) {
            try {
                retryCount = java.nio.ByteBuffer.wrap(attemptsBytes).getInt();
            } catch (Exception ignored) {}
        }

        log.error("Message index event moved to DLQ: messageId={}, originalTopic={}, error={}",
                event.messageId(), finalTopic, errorMessage);

        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            failedEventService.logFailure(
                    event.messageId(), EventType.MESSAGE_INDEX_REQUESTED,
                    finalTopic, finalPartition, finalOffset,
                    payloadJson, errorMessage, stackTrace, retryCount);
        } catch (Exception ex) {
            log.error("Critical error while logging message index failure to MongoDB", ex);
        }

        ack.acknowledge();
    }

    private MessageIndex toMessageIndex(MessageIndexRequestedEvent event) {
        return MessageIndex.builder()
                .id(event.messageId())
                .conversationId(event.conversationId())
                .senderId(event.senderId())
                .senderName(event.senderName())
                .senderAvatar(event.senderAvatar())
                .content(event.content())
                .originalFileName(event.originalFileName())
                .size(event.size())
                .searchableText(event.searchableText())
                .type(event.type())
                .status(event.status())
                .hasAttachment(event.hasAttachment())
                .hasLink(event.hasLink())
                .createdAt(event.createdAt())
                .deletedBy(event.deletedBy())
                .visibleTo(event.visibleTo())
                .build();
    }
}
