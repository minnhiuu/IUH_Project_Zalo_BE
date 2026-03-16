package com.bondhub.notificationservices.listener;

import com.bondhub.notificationservices.batch.BatcherService;
import com.bondhub.notificationservices.client.UserServiceClient;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.publisher.ReadyNotificationPublisher;
import com.bondhub.notificationservices.service.notification.NotificationService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import com.bondhub.common.event.notification.CleanupNotificationEvent;
import com.bondhub.common.event.notification.RawNotificationEvent;
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

    UserServiceClient userServiceClient;
    BatcherService batcherService;
    ReadyNotificationPublisher readyPublisher;
    UserPreferenceService userPreferenceService;
    NotificationService notificationService;

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

        log.info("[RawListener] Received event: topic={}, partition={}, offset={}", topic, partition, offset);

        RawNotificationEvent event = deserialize(message);
        if (event == null) {
            ack(acknowledgment);
            return;
        }

        try {
            var prefs = userPreferenceService.getPreferences(event.getRecipientId());
            if (prefs == null) {
                log.warn("[RawListener] Drop event: recipient {} not found or inactive", event.getRecipientId());
                ack(acknowledgment);
                return;
            }

            if (tryBatching(event)) {
                ack(acknowledgment);
                return;
            }

            dispatchToReadyQueue(event, prefs.getLanguage());
            ack(acknowledgment);

        } catch (Exception e) {
            log.error("[RawListener] Critical error processing event: {}", event.getRecipientId(), e);
            throw new RuntimeException("RawListener processing failed", e);
        }
    }

    @KafkaListener(
            topics = "#{notificationKafkaTopicConfig.cleanup}",
            groupId = "noti-cleanup-group",
            containerFactory = "pipelineListenerContainerFactory"
    )
    public void handleCleanupNotification(
            @Payload String message,
            Acknowledgment acknowledgment) {

        log.info("[CleanupListener] Received cleanup event");

        CleanupNotificationEvent event = deserializeCleanup(message);
        if (event == null) {
            ack(acknowledgment);
            return;
        }

        try {
            notificationService.deactivateByReferenceIdAndType(
                    event.getRecipientId(),
                    event.getReferenceId(),
                    event.getType()
            );
            ack(acknowledgment);
        } catch (Exception e) {
            log.error("[CleanupListener] Error processing cleanup for recipient: {}", event.getRecipientId(), e);
            throw new RuntimeException("CleanupListener failed", e);
        }
    }

    private CleanupNotificationEvent deserializeCleanup(String json) {
        try {
            return objectMapper.readValue(json, CleanupNotificationEvent.class);
        } catch (Exception e) {
            log.error("[CleanupListener] Json mapping failed: {}", json, e);
            return null;
        }
    }

    private boolean tryBatching(RawNotificationEvent event) {
        boolean buffered = batcherService.buffer(event);
        if (buffered) {
            log.debug("[RawListener] Event buffered for batching: type={}, recipient={}", 
                    event.getType(), event.getRecipientId());
        }
        return buffered;
    }

    private void dispatchToReadyQueue(RawNotificationEvent event, String locale) {
        BatchedNotificationEvent readyEvent = wrapAsReady(event, locale);
        readyPublisher.publish(readyEvent);
        log.info("[RawListener] Forwarded directly to Ready Queue: type={}, recipient={}", 
                event.getType(), event.getRecipientId());
    }

    private BatchedNotificationEvent wrapAsReady(RawNotificationEvent event, String locale) {

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
                .totalEventCount(1)
                .referenceId(event.getReferenceId())
                .lastActorId(event.getActorId())
                .lastActorName(event.getActorName())
                .lastActorAvatar(event.getActorAvatar())
                .othersCount(0)
                .locale(locale)
                .rawPayloads(List.of(payload))
                .lastOccurredAt(event.getOccurredAt())
                .batchedAt(LocalDateTime.now())
                .build();
    }

    private RawNotificationEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, RawNotificationEvent.class);
        } catch (Exception e) {
            log.error("[RawListener] Json mapping failed: {}", json, e);
            return null;
        }
    }

    private void ack(Acknowledgment ack) {
        if (ack != null) ack.acknowledge();
    }
}
