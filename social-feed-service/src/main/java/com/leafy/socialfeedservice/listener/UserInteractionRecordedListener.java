package com.leafy.socialfeedservice.listener;

import com.bondhub.common.event.socialfeed.UserInteractionEvent;
import com.leafy.socialfeedservice.model.UserInteraction;
import com.leafy.socialfeedservice.repository.UserInteractionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInteractionRecordedListener {

    UserInteractionRepository userInteractionRepository;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.interactionEvents.userInteraction}",
            groupId = "social-feed-user-interaction-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserInteractionRecorded(
            @Payload UserInteractionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            Acknowledgment acknowledgment) {

        log.info("Received user interaction event: topic={}, partition={}, offset={}, userId={}, postId={}, interactionType={}, weight={}, groupId={}",
                topic, partition, offset, event.userId(), event.postId(), event.interactionType(), event.weight(), event.groupId());

        if (isBlank(event.userId()) || isBlank(event.postId()) || event.interactionType() == null) {
            log.warn("Skipping invalid user interaction payload: topic={}, partition={}, offset={}, event={}",
                    topic, partition, offset, event);
            acknowledgment.acknowledge();
            return;
        }

        String documentId = buildDocumentId(topic, partition, offset);

        UserInteraction interaction = UserInteraction.builder()
                .id(documentId)
                .userId(event.userId().trim())
                .postId(event.postId().trim())
                .interactionType(event.interactionType())
                .weight(event.weight())
                .createdAt(event.createdAt() != null ? event.createdAt() : Instant.now())
                .groupId(normalizeGroupId(event.groupId()))
                .source(UserInteraction.SourceMetadata.builder()
                        .topic(topic)
                        .partition(partition)
                        .offset(offset)
                        .messageKey(messageKey)
                        .build())
                .ingestedAt(Instant.now())
                .build();

        try {
            userInteractionRepository.save(interaction);
            acknowledgment.acknowledge();
        } catch (DuplicateKeyException ex) {
            // Idempotency guard: replayed records from the same topic/partition/offset are treated as processed.
            log.info("User interaction already persisted, skipping duplicate: topic={}, partition={}, offset={}",
                    topic, partition, offset);
            acknowledgment.acknowledge();
        }
    }

    private String buildDocumentId(String topic, int partition, long offset) {
        return topic + ":" + partition + ":" + offset;
    }

    private String normalizeGroupId(String groupId) {
        if (isBlank(groupId)) {
            return null;
        }
        return groupId.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}