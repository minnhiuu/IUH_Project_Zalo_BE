package com.bondhub.notificationservices.publisher;

import com.bondhub.notificationservices.config.NotificationKafkaTopicConfig;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
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
public class ReadyNotificationPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;
    NotificationKafkaTopicConfig topicConfig;

    public void publish(BatchedNotificationEvent event) {
        try {
            String topic = topicConfig.getReady();
            kafkaTemplate.send(topic, event.getRecipientId(), event);
            log.debug("[Queue2] Published ready event: type={}, recipient={}, actors={}",
                    event.getType(), event.getRecipientId(), event.getActorCount());
        } catch (Exception e) {
            log.error("[Queue2] Failed to publish ready event: type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
        }
    }
}
