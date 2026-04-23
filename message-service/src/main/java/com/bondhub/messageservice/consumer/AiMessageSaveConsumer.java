package com.bondhub.messageservice.consumer;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.mapper.MessageMapper;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.S3UtilV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoTemplate mongoTemplate;
    private final S3UtilV2 s3UtilV2;



    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    @KafkaListener(topics = "ai.message.save", groupId = "message-service-save-group")
    public void handleAiMessageSave(AiMessageSaveEvent event, Acknowledgment ack) {
        log.info("[AI-Consumer] Received save event for chat: {}, sender: {}", event.getChatId(), event.getSenderId());

        // 1. Resolve type
        MessageType messageType = MessageType.CHAT;
        try {
            if (event.getType() != null) {
                messageType = MessageType.valueOf(event.getType());
            }
        } catch (IllegalArgumentException e) {
            log.warn("[AI-Consumer] Unknown message type: {}, falling back to CHAT", event.getType());
        }

        // 2. Persist Message into MongoDB
        Message aiMsg = Message.builder()
            .conversationId(event.getChatId())
            .senderId(event.getSenderId())
            .senderName(event.getSenderName() != null ? event.getSenderName() : "Bondhub AI")
            .content(event.getContent())
            .type(messageType)
            .status(MessageStatus.NORMAL)
            .createdAt(LocalDateTime.now())
            .build();

        Message savedMsg = messageRepository.save(aiMsg);

        // 3. Build Preview Info
        LastMessageInfo lastInfo = LastMessageInfo.builder()
                .messageId(savedMsg.getId())
                .senderId(savedMsg.getSenderId())
                .content(savedMsg.getContent())
                .type(savedMsg.getType())
                .status(savedMsg.getStatus())
                .timestamp(savedMsg.getCreatedAt())
                .build();

        // 4. Update Conversation state + Atomically increment unread counts for EVERYONE
        Query convQuery = new Query(Criteria.where("id").is(event.getChatId()));
        Update convUpdate = new Update().set("lastMessage", lastInfo);
        
        // Fetch to find members for fan-out (we can't easily iterate members in a single Mongo Update for unread counts)
        Conversation conversation = conversationRepository.findById(event.getChatId()).orElse(null);
        if (conversation == null) {
            log.error("[AI-Consumer] Conversation not found: {}", event.getChatId());
            ack.acknowledge();
            return;
        }

        // Increment unread counts for all members except the AI (no need) and maybe the person who asked?
        // Actually, AI responses usually increase unread count for everyone including the asker, 
        // because the asker is usually in SSE mode and might not have the DB message "read" yet.
        conversation.getMembers().forEach(m -> {
            if (m.getActive() != null && m.getActive()) {
                convUpdate.inc("unreadCounts." + m.getUserId(), 1);
            }
        });

        mongoTemplate.findAndModify(
                convQuery, convUpdate,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class);

        ack.acknowledge();
        log.info("[AI-Consumer] Persisted AI message and updated room state: {}", event.getChatId());

        // 5. FAN-OUT Real-time via WebSocket to ALL active members
        String baseUrl = s3UtilV2.getS3BaseUrl();
        ChatNotification baseNotif = messageMapper.mapToChatNotification(savedMsg, baseUrl, 0);

        conversation.getMembers().stream()
            .filter(m -> m.getActive() != null && m.getActive())
            .forEach(member -> {
                boolean isFromMe = member.getUserId().equals(event.getSenderId());
                
                // Note: unreadCount here is approximate since we didn't re-fetch the whole room after update,
                // but usually the frontend handles incrementing its own local state.
                
                ChatNotification personalNotif = baseNotif.toBuilder()
                        .isFromMe(isFromMe)
                        .build();

                kafkaTemplate.send(socketEventsTopic, new SocketEvent(
                        SocketEventType.MESSAGE,
                        member.getUserId(),
                        "/queue/messages",
                        personalNotif
                ));
            });
            
        log.info("[AI-Consumer] Broadcasted AI message to {} members", conversation.getMembers().size());
    }
}
