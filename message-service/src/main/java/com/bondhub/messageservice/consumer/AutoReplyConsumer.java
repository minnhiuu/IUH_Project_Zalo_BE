package com.bondhub.messageservice.consumer;

import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.event.chat.SendAutoReplyEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.service.message.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AutoReplyConsumer {

    private final SystemMessageService systemMessageService;
    private final ChatUserRepository chatUserRepository;

    @KafkaListener(topics = "chat.auto-reply", groupId = "message-auto-reply-group")
    public void handleAutoReply(SendAutoReplyEvent event, Acknowledgment ack) {
        log.info("[AutoReply Consumer] Received: conversation={}, quietUser={}, receiver={}",
                event.getConversationId(), event.getQuietUserId(), event.getReceiverId());

        try {
            // Resolve quiet user's display name
            String quietUserName = "Người dùng";
            ChatUser quietUser = chatUserRepository.findById(event.getQuietUserId()).orElse(null);
            if (quietUser != null && quietUser.getFullName() != null) {
                quietUserName = quietUser.getFullName();
            }

            Map<String, Object> extraMetadata = Map.of(
                    "quietUserId", event.getQuietUserId(),
                    "quietUserName", quietUserName,
                    "messageKey", event.getMessageKey() != null ? event.getMessageKey() : "chat.quiet_mode.auto_reply"
            );

            // Send system message visible only to the sender (receiverId)
            systemMessageService.sendSystemMessage(
                    event.getConversationId(),
                    event.getQuietUserId(),
                    quietUserName,
                    quietUser != null ? quietUser.getAvatar() : null,
                    SystemActionType.DND_AUTO_REPLY,
                    extraMetadata,
                    Set.of(event.getReceiverId())
            );

            log.info("[AutoReply Consumer] System message sent: conversation={}, visibleTo={}",
                    event.getConversationId(), event.getReceiverId());
        } catch (Exception e) {
            log.error("[AutoReply Consumer] Failed: conversation={}, error={}",
                    event.getConversationId(), e.getMessage(), e);
        }

        ack.acknowledge();
    }
}
