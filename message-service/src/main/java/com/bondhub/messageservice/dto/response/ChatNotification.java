package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import com.bondhub.common.enums.MessageType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.With;

/**
 * Chat Notification DTO
 * Uses Java Record as per backend development rules
 */
@Builder(toBuilder = true)
@With
public record ChatNotification(
                String id,
                String conversationId,
                String senderId,
                String senderName,
                String senderAvatar,
                String content,
                MessageType type,
                String clientMessageId,
                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+7")
                OffsetDateTime timestamp,
                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+7")
                OffsetDateTime expiredAt,
                Integer unreadCount,
                ReplyMetadataResponse replyTo,
                boolean isForwarded,
                boolean isFromMe,
                MessageStatus status,
                Map<String, Object> metadata,
                List<AttachmentInfoResponse> attachments,
                LinkPreviewResponse linkPreview,
                Map<String, List<String>> reactions) {
}
