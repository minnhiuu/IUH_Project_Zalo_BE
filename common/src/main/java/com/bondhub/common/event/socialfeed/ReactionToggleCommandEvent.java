package com.bondhub.common.event.socialfeed;

import lombok.Builder;

@Builder
public record ReactionToggleCommandEvent(
        String authorId,
        String targetId,
        String targetType,
        String reactionType,
        boolean desiredActive,
        Long timestamp
) {
}