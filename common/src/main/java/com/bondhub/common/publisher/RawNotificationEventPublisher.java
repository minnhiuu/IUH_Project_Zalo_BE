package com.bondhub.common.publisher;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.event.notification.CleanupNotificationEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RawNotificationEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;
    KafkaTopicProperties topicProperties;

    public void publish(RawNotificationEvent event) {
        try {
            String topic = topicProperties.getNotificationEvents().getRaw();
            kafkaTemplate.send(topic, event.getRecipientId(), event);
            log.debug("[Notification] Published raw event: type={}, recipient={}",
                    event.getType(), event.getRecipientId());
        } catch (Exception e) {
            log.error("[Notification] Failed to publish raw event: type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
            throw e;
        }
    }

    public void publishCleanup(CleanupNotificationEvent event) {
        try {
            String topic = topicProperties.getNotificationEvents().getCleanup();
            kafkaTemplate.send(topic, event.getRecipientId(), event);
            log.debug("[Notification] Published cleanup event: type={}, recipient={}",
                    event.getType(), event.getRecipientId());
        } catch (Exception e) {
            log.error("[Notification] Failed to publish cleanup event: type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
            throw e;
        }
    }
}
