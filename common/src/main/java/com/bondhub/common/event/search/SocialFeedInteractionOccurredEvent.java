package com.bondhub.common.event.search;

import com.bondhub.common.event.socialfeed.InteractionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialFeedInteractionOccurredEvent(
        String userId,
        String targetUserId,
        String postId,
        InteractionType interactionType,
        Instant occurredAt
) {
}
