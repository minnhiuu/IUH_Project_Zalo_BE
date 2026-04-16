package com.bondhub.notificationservices.publisher;

import com.bondhub.notificationservices.config.NotificationKafkaTopicConfig;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RawNotificationPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;
    NotificationKafkaTopicConfig topicConfig;

    public void publish(RawNotificationEvent event) {
        try {
            String topic = topicConfig.getRaw();
            kafkaTemplate.send(topic, event.getRecipientId(), event);
            log.debug("[Queue1] Published raw event: type={}, recipient={}",
                    event.getType(), event.getRecipientId());
        } catch (Exception e) {
            log.error("[Queue1] Failed to publish raw event: type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
        }
    }
}
