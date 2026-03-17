package com.leafy.socialfeedservice.dto.request.post;

import com.leafy.socialfeedservice.model.enums.Visibility;
import jakarta.validation.Valid;
import lombok.Builder;

import java.util.List;

@Builder
public record UpdatePostRequest(
        String title,
        String caption,
        String description,
        List<String> hashtags,
        @Valid
        List<PostMediaRequest> media,
        Visibility visibility
) {
}
