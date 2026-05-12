package com.bondhub.common.dto.client.messageservice;

import lombok.Builder;

import java.time.Instant;

@Builder
public record RecentChatInteractionResponse(
        String userId,
        Instant lastMessageAt,
        int messageCount30d,
        int sentByMeCount30d,
        int sentByTargetCount30d,
        boolean twoWayConversation,
        double volumeScore,
        double recencyBoost,
        double directChatScore,
        double groupInteractionScore,
        double chatInteractionScore
) {
}
