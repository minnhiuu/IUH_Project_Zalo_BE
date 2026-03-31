package com.bondhub.messageservice.consumer;

import com.bondhub.common.event.user.UserCreatedEvent;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ChatRoomRepository;
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
    private final ChatRoomRepository chatRoomRepository;

    @KafkaListener(topics = "user.created", groupId = "message-service-ai-group")
    public void handleUserCreated(UserCreatedEvent event, Acknowledgment ack) {
        log.info("Received user.created event for user: {}", event.getUserId());
        String recipientId = "ai-assistant-001";
        String senderId = event.getUserId();
        String conversationId = (senderId.compareTo(recipientId) < 0) 
                ? String.format("%s_%s", senderId, recipientId)
                : String.format("%s_%s", recipientId, senderId);
        
        if (chatRoomRepository.findByConversationId(conversationId).isEmpty()) {
            Conversation aiRoom = Conversation.builder()
                .conversationId(conversationId)
                .senderId(event.getUserId())
                .recipientId("ai-assistant-001")
                .members(Set.of(
                    ConversationMember.builder()
                        .userId(event.getUserId())
                        .role(MemberRole.OWNER)
                        .joinedAt(LocalDateTime.now())
                        .build(),
                    ConversationMember.builder()
                        .userId("ai-assistant-001")
                        .role(MemberRole.MEMBER)
                        .joinedAt(LocalDateTime.now())
                        .build()
                ))
                .isGroup(false)
                .build();
            chatRoomRepository.save(aiRoom);
            log.info("Initialized AI chat room for user: {}", event.getUserId());
        }

        // Tạo luôn phòng Cloud (My Documents) cho User
        String cloudChatId = event.getUserId() + "_" + event.getUserId();
        if (chatRoomRepository.findByConversationId(cloudChatId).isEmpty()) {
            Conversation cloudRoom = Conversation.builder()
                .conversationId(cloudChatId)
                .senderId(event.getUserId())
                .recipientId(event.getUserId()) // Sender và Recipient là một
                .members(Set.of(
                    ConversationMember.builder()
                        .userId(event.getUserId())
                        .role(MemberRole.OWNER)
                        .joinedAt(LocalDateTime.now())
                        .build()
                ))
                .isGroup(false)
                .build();
            chatRoomRepository.save(cloudRoom);
            log.info("Initialized Cloud (My Documents) room for user: {}", event.getUserId());
        }

        ack.acknowledge();
    }
}
