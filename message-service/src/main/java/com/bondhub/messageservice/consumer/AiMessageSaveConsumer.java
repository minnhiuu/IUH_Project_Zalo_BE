package com.bondhub.messageservice.consumer;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.repository.ChatMessageRepository;
import com.bondhub.messageservice.repository.ChatRoomRepository;
import com.bondhub.messageservice.mapper.MessageMapper;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.common.dto.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.utils.S3Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    @KafkaListener(topics = "ai.message.save", groupId = "message-service-save-group")
    public void handleAiMessageSave(AiMessageSaveEvent event, Acknowledgment ack) {
        log.info("Received ai.message.save event for chat: {}", event.getChatId());
        
        // Logic xác định người nhận để tránh lưu vào 'My Documents' (Nếu sender == userId thì người nhận phải là AI)
        String recipientId = event.getSenderId().equals(event.getUserId()) 
                ? "ai-assistant-001" 
                : event.getUserId();

        Message aiMsg = Message.builder()
            .conversationId(event.getChatId())
            .senderId(event.getSenderId()) 
            .recipientId(recipientId)
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
        log.info("Persisted AI-related message for chat: {}", event.getChatId());

        // BƯỚC 3: Đồng bộ hoá Real-time qua Socket (Để các thiết bị khác cũng thấy tin nhắn)
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        ChatNotification notification = messageMapper.mapToChatNotification(savedMsg, baseUrl, 0);
        
        // Phân phối tin nhắn đến tất cả các bên tham gia
        kafkaTemplate.send(socketEventsTopic, new SocketEvent(
                SocketEventType.MESSAGE, 
                event.getUserId(), 
                "/queue/messages", 
                notification.toBuilder().isFromMe(event.getSenderId().equals(event.getUserId())).build()
        ));
    }
}
