package com.bondhub.messageservice.listener;

import com.bondhub.common.event.user.UserCreatedEvent;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserCreatedKafkaListener {
    private final ChatRoomRepository chatRoomRepository;

    @KafkaListener(topics = "user.created", groupId = "message-service-ai-group")
    public void handleUserCreated(UserCreatedEvent event, Acknowledgment ack) {
        log.info("Received user.created event for user: {}", event.getUserId());
        String chatId = "ai-chat-" + event.getUserId();
        
        if (chatRoomRepository.findByChatId(chatId).isEmpty()) {
            Conversation aiRoom = Conversation.builder()
                .chatId(chatId)
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
        ack.acknowledge();
    }
}
