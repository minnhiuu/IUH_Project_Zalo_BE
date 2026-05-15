package com.bondhub.common.dto.client.socialfeedservice;

import lombok.Builder;

import java.time.Instant;

@Builder
public record SocialInteractionFeatureSnapshotResponse(
        String userId,
        String targetUserId,
        Instant lastInteractionAt,
        int viewCount30d,
        int reactionCount30d,
        int commentCount30d,
        int dislikeCount30d
) {
}
