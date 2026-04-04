package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.MessageType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record LastMessageResponse(
        String id,
        String senderId,
        String senderName,
        String content,
        LocalDateTime timestamp,
        MessageType type,
        MessageStatus status,
        boolean isFromMe,
        Map<String, Object> metadata
) {
}
