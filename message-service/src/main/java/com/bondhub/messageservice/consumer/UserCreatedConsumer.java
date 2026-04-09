package com.bondhub.messageservice.consumer;

import com.bondhub.common.event.user.UserCreatedEvent;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserCreatedConsumer {
    private final ConversationRepository conversationRepository;

    @KafkaListener(topics = "user.created", groupId = "message-service-ai-group")
    public void handleUserCreated(UserCreatedEvent event, Acknowledgment ack) {
        log.info("Received user.created event for user: {}", event.getUserId());
        LocalDateTime now = LocalDateTime.now();

        // Tạo phòng chat AI (user ↔ ai-assistant-001) nếu chưa có
        String aiAssistantId = "ai-assistant-001";
        conversationRepository.findDirectConversation(event.getUserId(), aiAssistantId)
                .orElseGet(() -> {
                    Conversation aiRoom = Conversation.builder()
                            .members(Set.of(
                                    ConversationMember.builder()
                                            .userId(event.getUserId())
                                            .role(MemberRole.OWNER)
                                            .joinedAt(now)
                                            .build(),
                                    ConversationMember.builder()
                                            .userId(aiAssistantId)
                                            .role(MemberRole.MEMBER)
                                            .joinedAt(now)
                                            .build()
                            ))
                            .lastMessage(LastMessageInfo.builder().timestamp(now).build())
                            .isGroup(false)
                            .build();
                    Conversation saved = conversationRepository.save(aiRoom);
                    log.info("Initialized AI chat room for user: {}", event.getUserId());
                    return saved;
                });

        // Tạo phòng Cloud (My Documents - chat với chính mình) nếu chưa có
        conversationRepository.findDirectConversation(event.getUserId(), event.getUserId())
                .orElseGet(() -> {
                    Conversation cloudRoom = Conversation.builder()
                            .members(Set.of(
                                    ConversationMember.builder()
                                            .userId(event.getUserId())
                                            .role(MemberRole.OWNER)
                                            .joinedAt(now)
                                            .build()
                            ))
                            .lastMessage(LastMessageInfo.builder().timestamp(now).build())
                            .isGroup(false)
                            .build();
                    Conversation saved = conversationRepository.save(cloudRoom);
                    log.info("Initialized Cloud (My Documents) room for user: {}", event.getUserId());
                    return saved;
                });

        ack.acknowledge();
    }
}
