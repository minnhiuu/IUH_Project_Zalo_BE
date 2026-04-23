package com.bondhub.common.publisher;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.event.notification.SystemNotificationEvent;
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
public class SystemNotificationEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;
    KafkaTopicProperties topicProperties;

    public void publish(SystemNotificationEvent event) {
        try {
            String topic = topicProperties.getNotificationEvents().getSystem();
            kafkaTemplate.send(topic, event.getRecipientId(), event);
            log.debug("[SystemNotification] Published event: type={}, category={}, recipient={}",
                    event.getType(), event.getCategory(), event.getRecipientId());
        } catch (Exception e) {
            log.error("[SystemNotification] Failed to publish event: type={}, category={}, recipient={}",
                    event.getType(), event.getCategory(), event.getRecipientId(), e);
            throw e;
        }
    }
}
