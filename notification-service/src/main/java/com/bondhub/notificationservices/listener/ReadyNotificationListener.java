package com.bondhub.notificationservices.listener;

import com.bondhub.notificationservices.config.NotificationKafkaTopicConfig;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.service.delivery.DeliveryService;
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

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReadyNotificationListener {

    NotificationKafkaTopicConfig topicConfig;
    DeliveryService deliveryService;
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "#{notificationKafkaTopicConfig.ready}",
            groupId = "noti-delivery-group",
            containerFactory = "deliveryListenerContainerFactory"
    )
    public void handleReadyNotification(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[Delivery] Received: topic={}, partition={}, offset={}", topic, partition, offset);

        BatchedNotificationEvent event;
        try {
            event = objectMapper.readValue(message, BatchedNotificationEvent.class);
        } catch (Exception e) {
            log.error("[Delivery] Deserialize failed, skipping: {}", message, e);
            if (acknowledgment != null) acknowledgment.acknowledge();
            return;
        }

        try {
            deliveryService.deliver(event);
            log.info("[Delivery] Sent: type={}, recipient={}, actors={}",
                    event.getType(), event.getRecipientId(), event.getActorCount());
            if (acknowledgment != null) acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[Delivery] Failed: type={}, recipient={}", event.getType(), event.getRecipientId(), e);
            throw new RuntimeException("Delivery failed", e);
        }
    }
}
