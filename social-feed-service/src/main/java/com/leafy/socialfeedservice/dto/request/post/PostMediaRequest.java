package com.leafy.socialfeedservice.dto.request.post;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record PostMediaRequest(
        @NotBlank(message = "media.url.required")
        String url,
        @NotBlank(message = "media.type.required")
        String type
) {
}
