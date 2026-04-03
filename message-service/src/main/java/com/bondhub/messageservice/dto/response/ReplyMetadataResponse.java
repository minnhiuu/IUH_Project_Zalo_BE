package com.bondhub.messageservice.dto.response;

import com.bondhub.common.enums.MessageType;
import lombok.Builder;

import lombok.With;

/**
 * Reply Metadata DTO for response enrichment
 */
@Builder(toBuilder = true)
@With
public record ReplyMetadataResponse(
        String messageId,
        String senderId,
        String senderName,
        String content,
        MessageType type) {
}
