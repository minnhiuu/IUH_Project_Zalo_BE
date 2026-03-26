package com.bondhub.socialfeedservice.dto.response.post;

import lombok.Builder;

@Builder
public record AuthorInfo(
        String id,
        String fullName,
        String avatar
) {
}
