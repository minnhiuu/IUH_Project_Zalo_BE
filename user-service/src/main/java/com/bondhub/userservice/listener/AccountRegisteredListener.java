package com.bondhub.userservice.listener;

import com.bondhub.common.event.account.AccountRegisteredEvent;
import com.bondhub.userservice.dto.request.user.UserCreateRequest;
import com.bondhub.userservice.dto.response.user.UserResponse;
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
public class AccountRegisteredListener {

    private final UserService userService;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.accountEvents.registered}",
            groupId = "user-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleAccountRegistered(
            @Payload AccountRegisteredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("📥 Received account registered event from Kafka: topic={}, partition={}, offset={}, accountId={}",
                topic, partition, offset, event.getAccountId());

        try {
            // Create user with fullName, accountId, phoneNumber and role
            UserCreateRequest request = UserCreateRequest.builder()
                    .accountId(event.getAccountId())
                    .fullName(event.getFullName())
                    .phoneNumber(event.getPhoneNumber())
                    .role("USER")
                    .build();

            UserResponse userResponse = userService.createUser(request);

            log.info("✅ User created successfully for accountId: {}, userId: {}", 
                    event.getAccountId(), userResponse.id());

            // Manual acknowledgment
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.debug("✅ Message acknowledged: offset={}", offset);
            }

        } catch (Exception e) {
            log.error("❌ Failed to create user for accountId: {}, error: {}", 
                    event.getAccountId(), e.getMessage(), e);
            
            // Don't acknowledge - message will be retried
            throw new RuntimeException("Failed to process account registered event", e);
        }
    }
}
