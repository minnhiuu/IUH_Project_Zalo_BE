package com.bondhub.socialfeedservice.dto.response.interaction;

import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import lombok.Builder;

import java.time.Instant;

import com.bondhub.socialfeedservice.model.enums.ReactionType;

@Builder
public record ViewerResponse(
        String id,
        AuthorInfo authorInfo,
        Instant viewedAt,
        ReactionType reactionType
) {
}
