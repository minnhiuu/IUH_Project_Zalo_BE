package com.bondhub.messageservice.dto.response;

import lombok.Builder;

@Builder
public record ReadReceiptNotification(
        String chatId,
        String userId,
        String lastReadMessageId
) {
}
