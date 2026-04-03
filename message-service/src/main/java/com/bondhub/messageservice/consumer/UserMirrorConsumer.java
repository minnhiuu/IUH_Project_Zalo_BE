package com.bondhub.messageservice.consumer;

import com.bondhub.common.event.user.UserPrivacyChangedEvent;
import com.bondhub.common.event.user.UserProfileUpdatedEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserMirrorConsumer {

    private final ChatUserRepository chatUserRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    @KafkaListener(topics = "${kafka.topics.user-events.updated}", groupId = "${spring.kafka.consumer.group-id:message-service-group}")
    public void handleUserUpdated(UserProfileUpdatedEvent event, Acknowledgment ack) {
        log.info("Received USER_UPDATED event for userId: {}", event.userId());
        try {
            chatUserRepository.findById(event.userId()).ifPresentOrElse(user -> {
                LocalDateTime eventTime = new Timestamp(event.timestamp()).toLocalDateTime();
                if (user.getLastUpdatedAt() == null || user.getLastUpdatedAt().isBefore(eventTime)) {
                    user.setFullName(event.fullName());
                    user.setAvatar(event.avatar());
                    user.setLastUpdatedAt(eventTime);
                    chatUserRepository.save(user);
                    log.info("✅ Updated ChatUser mirror for userId: {}", event.userId());
                } else {
                    log.info("⏩ Skipped outdated USER_UPDATED event for userId: {}", event.userId());
                }
            }, () -> {
                ChatUser newUser = ChatUser.builder()
                        .id(event.userId())
                        .fullName(event.fullName())
                        .avatar(event.avatar())
                        .lastUpdatedAt(new Timestamp(event.timestamp()).toLocalDateTime())
                        .build();
                chatUserRepository.save(newUser);
                log.info("✅ Created new ChatUser mirror for userId: {}", event.userId());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ Error processing USER_UPDATED event for userId: {}", event.userId(), e);
        }
    }

    @KafkaListener(topics = "${kafka.topics.user-events.privacy-changed}", groupId = "${spring.kafka.consumer.group-id:message-service-group}")
    public void handleUserPrivacyChanged(UserPrivacyChangedEvent event, Acknowledgment ack) {
        log.info("Received USER_PRIVACY_CHANGED event for userId: {}", event.userId());
        try {
            chatUserRepository.findById(event.userId()).ifPresent(user -> {
                LocalDateTime eventTime = new Timestamp(event.timestamp()).toLocalDateTime();
                if (user.getLastUpdatedAt() == null || user.getLastUpdatedAt().isBefore(eventTime)) {
                    user.setShowSeenStatus(event.showSeenStatus());
                    user.setLastUpdatedAt(eventTime);
                    chatUserRepository.save(user);
                    log.info("✅ Updated showSeenStatus to {} for userId: {}", event.showSeenStatus(), event.userId());

                    // Notify user themselves (multi-device)
                    publishSocketRefresh(event.userId());

                    // Notify recent conversation partners who are online
                    Pageable recentChats = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "lastMessage.timestamp"));
                    chatRoomRepository.findAllRoomsByUserId(event.userId(), recentChats)
                        .forEach(room -> {
                            String partnerId = room.getSenderId().equals(event.userId())
                                    ? room.getRecipientId() : room.getSenderId();
                            publishSocketRefresh(partnerId);
                        });
                } else {
                    log.info("⏩ Skipped outdated USER_PRIVACY_CHANGED event for userId: {}", event.userId());
                }
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ Error processing USER_PRIVACY_CHANGED event for userId: {}", event.userId(), e);
        }
    }

    private void publishSocketRefresh(String userId) {
        kafkaTemplate.send(socketEventsTopic,
                new SocketEvent(SocketEventType.NOTIFICATION, userId, "/queue/conversations", Map.of("type", "REFRESH")));
    }
}
