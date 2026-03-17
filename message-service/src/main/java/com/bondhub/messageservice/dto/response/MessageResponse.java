package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.messageservice.model.enums.MessageType;
import lombok.Builder;

import java.time.LocalDateTime;

import lombok.With;

@Builder(toBuilder = true)
@With
public record MessageResponse(
        String id,
        String chatId,
        String senderId,
        String senderName,
        String senderAvatar,
        String recipientId,
        String content,
        String clientMessageId,
        MessageType type,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt,
        ReplyMetadataResponse replyTo,
        boolean isForwarded,
        MessageStatus status
) {
}
