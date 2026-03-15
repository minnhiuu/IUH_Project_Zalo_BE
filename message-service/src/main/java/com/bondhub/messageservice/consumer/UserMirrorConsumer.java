package com.bondhub.messageservice.consumer;

import com.bondhub.common.event.user.UserPrivacyChangedEvent;
import com.bondhub.common.event.user.UserProfileUpdatedEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.user.SimpUserRegistry;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserMirrorConsumer {

    private final ChatUserRepository chatUserRepository;
    private final com.bondhub.messageservice.repository.ChatRoomRepository chatRoomRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    @KafkaListener(topics = "${kafka.topics.user-events.updated}", groupId = "${spring.kafka.consumer.group-id:message-service-group}")
    public void handleUserUpdated(UserProfileUpdatedEvent event, Acknowledgment ack) {
        log.info("Received USER_UPDATED event for userId: {}", event.userId());
        try {
            chatUserRepository.findById(event.userId()).ifPresentOrElse(user -> {
                LocalDateTime eventTime = new Timestamp(event.timestamp()).toLocalDateTime();
                // Idempotency: Only update if the event is newer than the last update
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
                // Cold start/New user: Create mirror entry
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
            // In a real production scenario, we might want to retry or send to a DLT
        }
    }

    @KafkaListener(topics = "${kafka.topics.user-events.privacy-changed}", groupId = "${spring.kafka.consumer.group-id:message-service-group}")
    public void handleUserPrivacyChanged(UserPrivacyChangedEvent event, Acknowledgment ack) {
        log.info("Received USER_PRIVACY_CHANGED event for userId: {}", event.userId());
        try {
            chatUserRepository.findById(event.userId()).ifPresent(user -> {
                LocalDateTime eventTime = new Timestamp(event.timestamp()).toLocalDateTime();
                // Idempotency check
                if (user.getLastUpdatedAt() == null || user.getLastUpdatedAt().isBefore(eventTime)) {
                    user.setShowSeenStatus(event.showSeenStatus());
                    user.setLastUpdatedAt(eventTime);
                    chatUserRepository.save(user);
                    log.info("✅ Updated showSeenStatus to {} for userId: {}", event.showSeenStatus(), event.userId());

                    // 1. Notify chính mình (đa thiết bị)
                    notifyIfOnline(event.userId());

                    // 2. Notify các đối tác đang ONLINE trong các cuộc hội thoại gần đây
                    Pageable recentChats = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "lastMessageTime"));
                    chatRoomRepository.findAllRoomsByUserId(event.userId(), recentChats)
                        .forEach(room -> {
                            String partnerId = room.getSenderId().equals(event.userId()) ? 
                                               room.getRecipientId() : room.getSenderId();
                            
                            notifyIfOnline(partnerId);
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

    private void notifyIfOnline(String userId) {
        // Chỉ bắn socket nếu user thực sự có kết nối active
        if (userRegistry.getUser(userId) != null) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/conversations",
                    Map.of("type", "REFRESH"));
        }
    }
}
