package com.bondhub.socialfeedservice.dto.request.comment;

import com.bondhub.socialfeedservice.dto.request.post.PostMediaRequest;
import jakarta.validation.Valid;
import lombok.Builder;

import java.util.List;

@Builder
public record UpdateCommentRequest(
        String content,
        @Valid
        List<PostMediaRequest> media
) {
}
