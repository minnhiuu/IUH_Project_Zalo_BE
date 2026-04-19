package com.bondhub.messageservice.publisher;

import com.bondhub.common.event.message.MessageIndexRequestedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.model.AttachmentInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.util.SearchableTextBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageIndexEventPublisher {

    OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publishIndexRequest(Message message) {
        if (message == null || message.getType() == MessageType.SYSTEM) {
            return;
        }

        String searchableText = SearchableTextBuilder.build(message);
        Instant createdAt = message.getCreatedAt() != null
                ? message.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null;
        AttachmentInfo primaryAttachment = resolvePrimaryAttachment(message);

        MessageIndexRequestedEvent event = MessageIndexRequestedEvent.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(message.getSenderAvatar())
                .content(message.getContent())
                .originalFileName(resolveOriginalFileName(primaryAttachment))
                .size(primaryAttachment != null ? primaryAttachment.getSize() : null)
                .searchableText(searchableText)
                .type(message.getType() != null ? message.getType().name() : null)
                .status(message.getStatus() != null ? message.getStatus().name() : null)
                .hasAttachment(message.getAttachments() != null && !message.getAttachments().isEmpty())
                .hasLink(message.getLinkPreview() != null)
                .createdAt(createdAt)
                .deletedBy(message.getDeletedBy() != null ? new ArrayList<>(message.getDeletedBy()) : List.of())
                .visibleTo(message.getVisibleTo() != null ? new ArrayList<>(message.getVisibleTo()) : List.of())
                .build();

        outboxEventPublisher.saveAndPublish(
                message.getId(),
                "Message",
                EventType.MESSAGE_INDEX_REQUESTED,
                event
        );

        log.info("Published MESSAGE_INDEX_REQUESTED: messageId={}", message.getId());
    }

    private AttachmentInfo resolvePrimaryAttachment(Message message) {
        if (message.getAttachments() == null || message.getAttachments().isEmpty()) {
            return null;
        }
        return message.getAttachments().get(0);
    }

    private String resolveOriginalFileName(AttachmentInfo attachmentInfo) {
        if (attachmentInfo == null) {
            return null;
        }
        return attachmentInfo.getOriginalFileName() != null
                ? attachmentInfo.getOriginalFileName()
                : attachmentInfo.getFileName();
    }
}
