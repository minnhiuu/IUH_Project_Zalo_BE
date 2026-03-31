package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.messageservice.model.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;

import lombok.With;

@Builder(toBuilder = true)
@With
public record MessageResponse(
        String id,
        String conversationId,
        String senderId,
        String senderName,
        String senderAvatar,
        String recipientId,
        String content,
        String clientMessageId,
        MessageType type,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
        LocalDateTime createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
        LocalDateTime lastModifiedAt,
        ReplyMetadataResponse replyTo,
        boolean isForwarded,
        MessageStatus status
) {
}
