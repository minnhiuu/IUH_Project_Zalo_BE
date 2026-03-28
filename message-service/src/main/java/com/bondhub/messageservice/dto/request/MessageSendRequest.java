package com.bondhub.messageservice.dto.request;

import com.bondhub.messageservice.model.ReplyMetadata;

/**
 * Request DTO for sending a chat message via REST POST /messages/send.
 * senderId is NOT included here – it is extracted from the JWT via SecurityUtil in the service layer.
 */
public record MessageSendRequest(
        String recipientId,
        String content,
        String clientMessageId,
        ReplyMetadata replyTo,
        boolean isForwarded
) {
}
