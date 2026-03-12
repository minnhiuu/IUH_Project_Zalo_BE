package com.bondhub.notificationservices.listener;

import com.bondhub.notificationservices.config.NotificationKafkaTopicConfig;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.notificationservices.pipeline.NotificationBatcherStep;
import com.bondhub.notificationservices.pipeline.UserPreferenceCheckerStep;
import com.bondhub.notificationservices.pipeline.UserValidatorStep;
import com.bondhub.notificationservices.publisher.ReadyNotificationPublisher;
import com.bondhub.notificationservices.service.preference.UserPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RawNotificationListener {

    NotificationKafkaTopicConfig topicConfig;
    UserValidatorStep userValidatorStep;
    NotificationBatcherStep notificationBatcherStep;
    UserPreferenceCheckerStep userPreferenceCheckerStep;
    ReadyNotificationPublisher readyPublisher;
    UserPreferenceService userPreferenceService;
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "#{notificationKafkaTopicConfig.raw}",
            groupId = "noti-pipeline-group",
            containerFactory = "pipelineListenerContainerFactory"
    )
    public void handleRawNotification(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[Pipeline] Received: topic={}, partition={}, offset={}", topic, partition, offset);

        RawNotificationEvent event;
        try {
            event = objectMapper.readValue(message, RawNotificationEvent.class);
        } catch (Exception e) {
            log.error("[Pipeline] Deserialize failed, skipping: {}", message, e);
            if (acknowledgment != null) acknowledgment.acknowledge();
            return;
        }

        try {
            if (!userValidatorStep.process(event)) {
                log.debug("[Pipeline] Dropped by validator: recipient={}", event.getRecipientId());
                if (acknowledgment != null) acknowledgment.acknowledge();
                return;
            }

            if (!notificationBatcherStep.process(event)) {
                log.debug("[Pipeline] Buffered for batching: type={}, recipient={}", event.getType(), event.getRecipientId());
                if (acknowledgment != null) acknowledgment.acknowledge();
                return;
            }

            if (!userPreferenceCheckerStep.process(event)) {
                log.debug("[Pipeline] Dropped by preference: recipient={}", event.getRecipientId());
                if (acknowledgment != null) acknowledgment.acknowledge();
                return;
            }

            readyPublisher.publish(toImmediate(event));
            log.info("[Pipeline] Forwarded to Queue2: type={}, recipient={}", event.getType(), event.getRecipientId());
            if (acknowledgment != null) acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("[Pipeline] Processing failed: type={}, recipient={}", event.getType(), event.getRecipientId(), e);
            throw new RuntimeException("Pipeline processing failed", e);
        }
    }

    private BatchedNotificationEvent toImmediate(RawNotificationEvent event) {
        String locale = userPreferenceService.getLocale(event.getRecipientId());

        Map<String, Object> payload = new HashMap<>(
                event.getPayload() != null ? event.getPayload() : Collections.emptyMap()
        );
        payload.put("actorId", event.getActorId());
        payload.put("referenceId", event.getReferenceId());
        payload.put("occurredAt", event.getOccurredAt() != null ? event.getOccurredAt().toString() : null);

        return BatchedNotificationEvent.builder()
                .recipientId(event.getRecipientId())
                .type(event.getType())
                .actorIds(List.of(event.getActorId()))
                .actorCount(1)
                .referenceId(event.getReferenceId())
                .lastActorId(event.getActorId())
                .lastActorName(event.getActorName())
                .lastActorAvatar(event.getActorAvatar())
                .othersCount(0)
                .locale(locale)
                .rawPayloads(List.of(payload))
                .batchedAt(LocalDateTime.now())
                .build();
    }
}
