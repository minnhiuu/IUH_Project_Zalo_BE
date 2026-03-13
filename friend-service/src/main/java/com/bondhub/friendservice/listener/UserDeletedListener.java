package com.bondhub.friendservice.listener;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.event.user.UserDeletedEvent;
import com.bondhub.friendservice.repository.FriendShipRepository;
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
public class UserDeletedListener {

    private final FriendShipRepository friendShipRepository;
    private final KafkaTopicProperties kafkaTopicProperties;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.userEvents.deleted}",
            groupId = "friend-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserDeleted(
            @Payload UserDeletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("📥 Received user deleted event from Kafka: topic={}, partition={}, offset={}, userId={}",
                topic, partition, offset, event.getUserId());

        try {
            // Delete all friendships involving this user
            var friendships = friendShipRepository.findAllFriendshipsByUserId(event.getUserId());
            
            if (!friendships.isEmpty()) {
                friendShipRepository.deleteAll(friendships);
                log.info("✅ Deleted {} friendships for userId: {}", friendships.size(), event.getUserId());
            } else {
                log.info("ℹ️ No friendships found for userId: {}", event.getUserId());
            }

            // Manual acknowledgment
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.debug("✅ Message acknowledged: offset={}", offset);
            }

        } catch (Exception e) {
            log.error("❌ Failed to delete friendships for userId: {}, error: {}", 
                    event.getUserId(), e.getMessage(), e);
            
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to process user deleted event", e);
        }
    }
}
