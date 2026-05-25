package com.bondhub.messageservice.consumer;

import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.common.event.user.UserPrivacyChangedEvent;
import com.bondhub.common.event.user.UserProfileUpdatedEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
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
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserMirrorConsumer {

    private final ChatUserRepository chatUserRepository;
    private final ConversationRepository conversationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    private final S3UtilV2 s3UtilV2;

    @KafkaListener(topics = "${kafka.topics.user-events.updated}", groupId = "${spring.kafka.consumer.group-id:message-service-group}")
    public void handleUserUpdated(Object event, Acknowledgment ack) {
        if (!(event instanceof UserProfileUpdatedEvent profileEvent)) {
            log.debug("Skipping non-profile update event of type: {}", event.getClass().getName());
            ack.acknowledge();
            return;
        }

        log.info("Received USER_UPDATED event for userId: {}", profileEvent.userId());
        try {
            chatUserRepository.findById(profileEvent.userId()).ifPresentOrElse(user -> {
                LocalDateTime eventTime = new Timestamp(profileEvent.timestamp()).toLocalDateTime();
                if (user.getLastUpdatedAt() == null || user.getLastUpdatedAt().isBefore(eventTime)) {
                    if (StringUtils.hasText(profileEvent.fullName())) {
                        user.setFullName(profileEvent.fullName());
                    }
                    if (StringUtils.hasText(profileEvent.avatar())) {
                        user.setAvatar(s3UtilV2.extractStorageKey(profileEvent.avatar()));
                        log.info("✅ Updated ChatUser mirror with avatar: {}", user.getAvatar());
                    }
                    user.setLastUpdatedAt(eventTime);
                    chatUserRepository.save(user);
                    log.info("✅ Updated ChatUser mirror for userId: {}", profileEvent.userId());
                } else {
                    log.info("⏩ Skipped outdated USER_UPDATED event for userId: {}", profileEvent.userId());
                }
            }, () -> {
                ChatUser newUser = ChatUser.builder()
                        .id(profileEvent.userId())
                        .fullName(profileEvent.fullName())
                        .avatar(profileEvent.avatar())
                        .phoneNumber(profileEvent.phoneNumber())
                        .fullName(StringUtils.hasText(profileEvent.fullName()) ? profileEvent.fullName() : "Người dùng mới")
                    .avatar(StringUtils.hasText(profileEvent.avatar())
                        ? s3UtilV2.extractStorageKey(profileEvent.avatar())
                        : null)
                        .lastUpdatedAt(new Timestamp(profileEvent.timestamp()).toLocalDateTime())
                        .build();
                chatUserRepository.save(newUser);
                log.info("✅ Created new ChatUser mirror for userId: {}", profileEvent.userId());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ Error processing USER_UPDATED event for userId: {}", profileEvent.userId(), e);
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
                    conversationRepository.findAllByMembersUserId(event.userId(), recentChats)
                        .forEach(room ->
                            room.getMembers().stream()
                                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                                .map(m -> m.getUserId())
                                .filter(uid -> !uid.equals(event.userId()))
                                .forEach(this::publishSocketRefresh)
                        );
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
