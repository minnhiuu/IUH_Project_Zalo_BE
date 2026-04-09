package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.With;

@Builder(toBuilder = true)
@With
public record MessageResponse(
                String id,
                String conversationId,
                String senderId,
                String senderName,
                String senderAvatar,
                String content,
                String clientMessageId,
                MessageType type,
                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+7")
                OffsetDateTime createdAt,
                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+7")
                OffsetDateTime lastModifiedAt,
                ReplyMetadataResponse replyTo,
                boolean isForwarded,
                MessageStatus status,
                Map<String, Object> metadata) {
}
