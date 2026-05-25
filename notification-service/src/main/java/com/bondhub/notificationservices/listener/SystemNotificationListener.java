package com.bondhub.notificationservices.listener;

import com.bondhub.common.event.notification.SystemNotificationEvent;
import com.bondhub.notificationservices.service.delivery.SystemNotificationDeliveryService;
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
public class SystemNotificationListener {

    SystemNotificationDeliveryService deliveryService;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "#{notificationKafkaTopicConfig.system}",
            groupId = "noti-system-group",
            containerFactory = "pipelineListenerContainerFactory"
    )
    public void handleSystemNotification(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[SystemListener] Received event: topic={}, partition={}, offset={}", topic, partition, offset);

        SystemNotificationEvent event = deserialize(message);
        if (event == null) {
            ack(acknowledgment);
            return;
        }

        try {
            deliveryService.deliver(event);
            ack(acknowledgment);
        } catch (Exception e) {
            log.error("[SystemListener] Error processing event: type={}, category={}, recipient={}",
                    event.getType(), event.getCategory(), event.getRecipientId(), e);
            throw new RuntimeException("SystemListener processing failed", e);
        }
    }

    private SystemNotificationEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, SystemNotificationEvent.class);
        } catch (Exception e) {
            log.error("[SystemListener] Json mapping failed: {}", json, e);
            return null;
        }
    }

    private void ack(Acknowledgment ack) {
        if (ack != null) ack.acknowledge();
    }
}
