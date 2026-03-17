package com.leafy.socialfeedservice.dto.request.comment;

import com.leafy.socialfeedservice.dto.request.post.PostMediaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
public record CreateCommentRequest(
        @NotBlank(message = "comment.postId.required")
        String postId,
        String parentId,
        @NotBlank(message = "comment.content.required")
        String content,
        @Valid
        List<PostMediaRequest> media
) {
}
