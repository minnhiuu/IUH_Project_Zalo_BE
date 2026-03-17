package com.leafy.socialfeedservice.dto.response.comment;

import com.leafy.socialfeedservice.model.embedded.PostMedia;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CommentResponse(
        String id,
        String postId,
        String authorId,
        String parentId,
        String content,
        List<PostMedia> media,
        int replyDepth,
        int replyCount,
        int reactionCount,
        boolean isEdited,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}
