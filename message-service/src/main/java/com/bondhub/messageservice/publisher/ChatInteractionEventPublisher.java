package com.bondhub.messageservice.publisher;

import com.bondhub.common.event.search.ChatInteractionOccurredEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.Message;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatInteractionEventPublisher {

    OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publishDirectChatInteraction(Message message, Conversation conversation) {
        if (message == null || conversation == null || conversation.isGroup()) {
            return;
        }

        String senderId = message.getSenderId();
        String targetUserId = resolveDirectTargetUserId(conversation, senderId);
        if (senderId == null || targetUserId == null) {
            return;
        }

        ChatInteractionOccurredEvent event = ChatInteractionOccurredEvent.builder()
                .userId(senderId)
                .targetUserId(targetUserId)
                .conversationId(conversation.getId())
                .occurredAt(message.getCreatedAt() != null ? message.getCreatedAt().toInstant(ZoneOffset.UTC) : Instant.now())
                .build();

        outboxEventPublisher.saveAndPublish(
                message.getId(),
                "Message",
                EventType.CHAT_INTERACTION_OCCURRED,
                event
        );

        log.debug("Published CHAT_INTERACTION_OCCURRED: userId={}, targetUserId={}, conversationId={}",
                senderId, targetUserId, conversation.getId());
    }

    private String resolveDirectTargetUserId(Conversation conversation, String senderId) {
        if (conversation.getMembers() == null) {
            return null;
        }

        return conversation.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .map(ConversationMember::getUserId)
                .filter(Objects::nonNull)
                .filter(userId -> !userId.equals(senderId))
                .findFirst()
                .orElse(null);
    }
}
