package com.bondhub.socialfeedservice.dto.request.post;

import com.bondhub.socialfeedservice.model.enums.Visibility;
import jakarta.validation.Valid;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record UpdatePostRequest(
        String title,
        String caption,
        String description,
        List<String> hashtags,
        @Valid
        List<PostMediaRequest> media,
        @Valid
        PostContentRequest sharedCaption,
        LocalDateTime expiresAt,
        PostMusicRequest music,
        List<String> viewerIds,
        @Valid
        LocationInfoRequest location,
        @Valid
        List<StoryElementRequest> elements,
        Visibility visibility
) {
}
