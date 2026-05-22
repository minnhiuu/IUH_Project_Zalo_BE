package com.bondhub.searchservice.listener;

import com.bondhub.common.event.search.ChatInteractionOccurredEvent;
import com.bondhub.common.event.search.SocialFeedInteractionOccurredEvent;
import com.bondhub.searchservice.service.interactionfeature.UserInteractionFeatureService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
public class UserInteractionFeatureEventListener {

    UserInteractionFeatureService userInteractionFeatureService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "#{kafkaTopicProperties.interactionEvents.chatInteractionOccurred}",
            groupId = "search-service-interaction-feature-group",
            concurrency = "3"
    )
    public void handleChatInteraction(
            @Payload ChatInteractionOccurredEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.debug("Processing CHAT_INTERACTION_OCCURRED: userId={}, targetUserId={}, partition={}, offset={}",
                event.userId(), event.targetUserId(), partition, offset);

        userInteractionFeatureService.recordChatInteraction(event);
        ack.acknowledge();
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
            topics = "#{kafkaTopicProperties.interactionEvents.socialFeedInteractionOccurred}",
            groupId = "search-service-interaction-feature-group",
            concurrency = "3"
    )
    public void handleSocialFeedInteraction(
            @Payload SocialFeedInteractionOccurredEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.debug("Processing SOCIAL_FEED_INTERACTION_OCCURRED: userId={}, targetUserId={}, type={}, partition={}, offset={}",
                event.userId(), event.targetUserId(), event.interactionType(), partition, offset);

        userInteractionFeatureService.recordSocialFeedInteraction(event);
        ack.acknowledge();
    }

    @DltHandler
    public void handleDlq(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] errorMsgBytes,
            Acknowledgment ack) {

        String errorMessage = errorMsgBytes != null ? new String(errorMsgBytes) : "Unknown error";
        log.error("Interaction feature event moved to DLQ: topic={}, partition={}, offset={}, payloadType={}, error={}",
                topic, partition, offset, event != null ? event.getClass().getSimpleName() : "null", errorMessage);
        ack.acknowledge();
    }
}
