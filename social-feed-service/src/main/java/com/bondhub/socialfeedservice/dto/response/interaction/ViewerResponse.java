package com.bondhub.socialfeedservice.dto.response.interaction;

import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ViewerResponse(
        String id,
        AuthorInfo authorInfo,
        Instant viewedAt
) {
}
