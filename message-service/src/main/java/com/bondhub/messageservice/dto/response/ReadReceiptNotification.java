package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record ReadReceiptNotification(
        String conversationId,
        String userId,
        String lastReadMessageId
) {
}
