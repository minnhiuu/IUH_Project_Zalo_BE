package com.bondhub.searchservice.listener;

import com.bondhub.common.event.message.MessageIndexRequestedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.searchservice.model.elasticsearch.MessageIndex;
import com.bondhub.searchservice.repository.elasticsearch.MessageSearchRepository;
import com.bondhub.searchservice.service.failevent.FailedEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageIndexRequestedListener {

    MessageSearchRepository messageSearchRepository;
    FailedEventService failedEventService;
    ObjectMapper objectMapper;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.messageEvents.indexRequested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(
            @Payload MessageIndexRequestedEvent event,
            @Header("kafka_receivedMessageKey") String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        log.info("Received MESSAGE_INDEX_REQUESTED: messageId={}, key={}", event.messageId(), key);

        try {
            MessageIndex messageIndex = toMessageIndex(event);
            messageSearchRepository.save(messageIndex);
            log.info("Successfully indexed message: {}", event.messageId());
        } catch (Exception e) {
            log.error("Failed to index message: {}", event.messageId(), e);
            logMessageIndexFailure(event, topic, partition, offset, e, ack);
            return;
        }

        ack.acknowledge();
    }

    private void logMessageIndexFailure(MessageIndexRequestedEvent event, String topic, int partition, long offset, Exception e, Acknowledgment ack) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            String errorMessage = e.getMessage();
            String stackTrace = org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
            int retryCount = 0; 

            failedEventService.logFailure(event.messageId(), EventType.MESSAGE_INDEX_REQUESTED, 
                    topic, partition, offset, payloadJson, errorMessage, stackTrace, retryCount);
        } catch (Exception ex) {
            log.error("Critical error while logging message index failure to MongoDB", ex);
        }

        ack.acknowledge();
    }

    private MessageIndex toMessageIndex(MessageIndexRequestedEvent event) {
        return MessageIndex.builder()
                .id(event.messageId())
                .conversationId(event.conversationId())
                .participantIds(event.participantIds())
                .participantNames(event.participantNames())
                .participantAvatars(event.participantAvatars())
                .conversationName(event.conversationName())
                .conversationAvatar(event.conversationAvatar())
                .group(event.group())
                .senderId(event.senderId())
                .senderName(event.senderName())
                .senderAvatar(event.senderAvatar())
                .content(event.content())
                .linkGroupName(event.linkGroupName())
                .linkUrl(event.linkUrl())
                .originalFileName(event.originalFileName())
                .fileExtension(event.fileExtension())
                .size(event.size())
                .searchableText(event.searchableText())
                .conversationSearchText(event.conversationSearchText())
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
