package com.bondhub.socialfeedservice.dto.request.post;

import com.bondhub.socialfeedservice.model.enums.PostType;
import com.bondhub.socialfeedservice.model.enums.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CreatePostRequest(
        String groupId,
        String title,
        String caption,
        String description,
        List<String> hashtags,
        @Valid
        List<PostMediaRequest> media,
        @NotNull(message = "post.type.required")
        PostType postType,
        String sharedPostId,
        @Valid
        PostContentRequest sharedCaption,
        LocalDateTime expiresAt,
        PostMusicRequest music,
        List<String> viewerIds,
        @Valid
        LocationInfoRequest location,
        @Valid
        List<StoryElementRequest> elements,
        @NotNull(message = "post.visibility.required")
        Visibility visibility
) {
}
