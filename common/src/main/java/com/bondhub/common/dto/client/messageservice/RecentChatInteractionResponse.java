package com.bondhub.common.dto.client.messageservice;

import lombok.Builder;

import java.time.Instant;

@Builder
public record RecentChatInteractionResponse(
        String userId,
        Instant lastMessageAt,
        int messageCount30d,
        double chatInteractionScore
) {
}
