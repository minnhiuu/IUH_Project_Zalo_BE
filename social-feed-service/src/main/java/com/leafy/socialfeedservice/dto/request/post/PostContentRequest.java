package com.leafy.socialfeedservice.dto.request.post;

import lombok.Builder;

import java.util.List;

@Builder
public record PostContentRequest(
        String title,
        String caption,
        String description,
        List<String> hashtags
) {
}
