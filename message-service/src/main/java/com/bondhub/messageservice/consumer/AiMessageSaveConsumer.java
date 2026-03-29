package com.bondhub.messageservice.consumer;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.enums.MessageType;
import com.bondhub.messageservice.repository.ChatMessageRepository;
import com.bondhub.messageservice.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiMessageSaveConsumer {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    @KafkaListener(topics = "ai.message.save", groupId = "message-service-save-group")
    public void handleAiMessageSave(AiMessageSaveEvent event, Acknowledgment ack) {
        log.info("Received ai.message.save event for chat: {}", event.getChatId());
        
        Message aiMsg = Message.builder()
            .conversationId(event.getChatId())
            .senderId("ai-assistant-001")
            .recipientId(event.getUserId())
            .content(event.getContent())
            .type(MessageType.CHAT)
            .status(MessageStatus.NORMAL)
            .createdAt(LocalDateTime.now())
            .build();
            
        Message savedMsg = chatMessageRepository.save(aiMsg);
        
        // Update LastMessageInfo in Conversation
        chatRoomRepository.findByConversationId(event.getChatId()).ifPresent(conversation -> {
            conversation.setLastMessage(LastMessageInfo.builder()
                .messageId(savedMsg.getId())
                .senderId(savedMsg.getSenderId())
                .content(savedMsg.getContent())
                .type(savedMsg.getType())
                .status(savedMsg.getStatus())
                .timestamp(savedMsg.getCreatedAt())
                .build());
            chatRoomRepository.save(conversation);
        });
        
        ack.acknowledge();
        log.info("Persisted AI message for chat: {}", event.getChatId());
    }
}
