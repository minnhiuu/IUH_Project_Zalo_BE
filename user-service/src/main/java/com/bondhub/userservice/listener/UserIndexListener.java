package com.bondhub.userservice.listener;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.event.user.UserIndexEvent;
import com.bondhub.userservice.dto.request.UserIndexRequest;
import com.bondhub.userservice.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserIndexListener {

    private final UserService userService;
    private final KafkaTopicProperties kafkaTopicProperties;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.userEvents.index}",
            groupId = "user-service-index-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserIndexEvent(
            @Payload UserIndexEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("📥 Received user index event from Kafka: topic={}, partition={}, offset={}, userId={}",
                topic, partition, offset, event.getUserId());

        try {
            UserIndexRequest indexRequest = UserIndexRequest.builder()
                    .userId(event.getUserId())
                    .phoneNumber(event.getPhoneNumber())
                    .role(event.getRole())
                    .build();
            
            userService.indexUserToElasticsearch(indexRequest);

            log.info("✅ User indexed successfully in Elasticsearch for userId: {}", event.getUserId());

            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.debug("✅ Message acknowledged: offset={}", offset);
            }

        } catch (Exception e) {
            log.error("❌ Failed to process user index event for userId: {}", event.getUserId(), e);
            throw new RuntimeException("Failed to process user index event", e);
        }
    }
}
