package com.bondhub.common.dto.client.messageservice;

/**
 * Request DTO for sending a chat message. 
 * shared between message-service and ai-service.
 */
public record MessageSendRequest(
        String recipientId,
        String content,
        String clientMessageId,
        ReplyMetadata replyTo,
        boolean isForwarded
) {
}
