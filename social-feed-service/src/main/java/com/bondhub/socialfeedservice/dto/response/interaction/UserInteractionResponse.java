package com.bondhub.socialfeedservice.dto.response.interaction;

import com.bondhub.common.event.socialfeed.InteractionType;
import lombok.Builder;

import java.time.Instant;

@Builder
public record UserInteractionResponse(
        String id,
        String userId,
        String postId,
        String groupId,
        InteractionType interactionType,
        float weight,
        Instant createdAt,
        Instant ingestedAt
) {
}
