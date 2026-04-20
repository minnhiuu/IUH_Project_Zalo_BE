package com.bondhub.socialfeedservice.dto.request.comment;

import com.bondhub.socialfeedservice.dto.request.post.PostMediaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder
public record UpdateCommentRequest(
        @NotBlank(message = "comment.content.required")
        String content,
        @Valid
        List<PostMediaRequest> media
) {
}
