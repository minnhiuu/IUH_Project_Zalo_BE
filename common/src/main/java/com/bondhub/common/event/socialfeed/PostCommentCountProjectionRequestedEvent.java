package com.bondhub.common.event.socialfeed;

import lombok.Builder;

@Builder
public record PostCommentCountProjectionRequestedEvent(
        String actorId,
        String postId,
        String commentId,
        String action,
        Long timestamp
) {
}
