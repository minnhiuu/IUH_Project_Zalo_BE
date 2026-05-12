package com.bondhub.common.dto.client.messageservice;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ChatInteractionFeatureSnapshotResponse(
        String userId,
        String targetUserId,
        int messageCount30d,
        Instant lastMessageAt
) {
}
