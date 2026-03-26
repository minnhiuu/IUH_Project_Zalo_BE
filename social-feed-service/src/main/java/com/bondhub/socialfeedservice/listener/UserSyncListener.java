package com.bondhub.socialfeedservice.listener;

import com.bondhub.common.event.user.UserCreatedEvent;
import com.bondhub.common.event.user.UserUpdatedEvent;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.repository.UserSummaryRepository;
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
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSyncListener {

    UserSummaryRepository userSummaryRepository;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.userEvents.created}",
            groupId = "social-feed-user-sync-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserCreated(
            @Payload UserCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received USER_CREATED event: topic={}, partition={}, offset={}, userId={}",
                topic, partition, offset, event.getUserId());

        if (event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("Skipping USER_CREATED with blank userId");
            acknowledgment.acknowledge();
            return;
        }

        UserSummary summary = UserSummary.builder()
                .id(event.getUserId())
                .fullName(event.getFullName())
                .avatar(event.getAvatar())
                .build();

        userSummaryRepository.save(summary);
        log.info("Upserted UserSummary for userId={}", event.getUserId());

        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "#{kafkaTopicProperties.userEvents.updated}",
            groupId = "social-feed-user-sync-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserUpdated(
            @Payload UserUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received USER_UPDATED event: topic={}, partition={}, offset={}, userId={}",
                topic, partition, offset, event.getUserId());

        if (event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("Skipping USER_UPDATED with blank userId");
            acknowledgment.acknowledge();
            return;
        }

        UserSummary summary = userSummaryRepository.findById(event.getUserId())
                .orElseGet(() -> UserSummary.builder().id(event.getUserId()).build());

        summary.setFullName(event.getFullName());
        summary.setAvatar(event.getAvatar());

        userSummaryRepository.save(summary);
        log.info("Updated UserSummary for userId={}", event.getUserId());

        acknowledgment.acknowledge();
    }
}
