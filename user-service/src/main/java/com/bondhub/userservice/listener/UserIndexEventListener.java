package com.bondhub.userservice.listener;

import com.bondhub.common.event.user.UserIndexDeletedEvent;
import com.bondhub.common.event.user.UserIndexRequestedEvent;
import com.bondhub.userservice.config.ElasticsearchProperties;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
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
public class UserIndexEventListener {
    ElasticsearchOperations esOperations;
    ElasticsearchProperties esProperties;

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
            groupId = "user-search-indexer-group",
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
            log.debug("Indexed user: {}", event.userId());

        } catch (Exception e) {
            log.error("Failed to index user: userId={}", event.userId(), e);
            throw e;
        }
    }

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
            ack.acknowledge();
            log.debug("Deleted user from index: {}", event.userId());

        } catch (Exception e) {
            log.error("Failed to delete user: userId={}", event.userId(), e);
            throw e;
        }
    }

    @DltHandler
    public void handleIndexRequestDLQ(
            @Payload UserIndexRequestedEvent event,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Event in DLQ: userId={}, reason={}", event.userId(), exceptionMessage);
    }

    @DltHandler
    public void handleDeleteRequestDLQ(
            @Payload UserIndexDeletedEvent event,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Delete event in DLQ: userId={}, reason={}", event.userId(), exceptionMessage);
    }

    private UserIndex convertToUserIndex(UserIndexRequestedEvent event) {
        return UserIndex.builder()
                .id(event.userId())
                .fullName(event.fullName())
                .phoneNumber(event.phoneNumber())
                .accountId(event.accountId())
                .role(event.role().getName())
                .avatar(event.avatar())
                .build();
    }
}
