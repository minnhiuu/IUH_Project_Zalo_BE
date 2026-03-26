package com.bondhub.socialfeedservice.dto.request.post;

import lombok.Builder;

import java.util.Map;

@Builder
public record StoryElementRequest(
        String type,
        String text,
        String url,
        Double x,
        Double y,
        Map<String, Object> metadata
) {
}
