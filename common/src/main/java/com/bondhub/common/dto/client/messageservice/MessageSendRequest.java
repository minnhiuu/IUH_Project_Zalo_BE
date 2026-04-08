package com.bondhub.common.dto.client.messageservice;

/**
 * Request DTO for sending a chat message.
 * Shared between message-service and ai-service.
 * conversationId is the MongoDB ObjectId (_id) of the target Conversation.
 */
public record MessageSendRequest(
        String conversationId, // ObjectId of the target Conversation
        String recipientId,    // Used for the first message (1-1) when conversation is not yet created
        String content,
        String clientMessageId,
        ReplyMetadata replyTo,
        boolean isForwarded
) {
}
