package com.bondhub.socialfeedservice.dto.response.comment;

import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CommentResponse(
        String id,
        String postId,
        AuthorInfo authorInfo,
        String parentId,
        String content,
        List<PostMedia> media,
        int replyDepth,
        int replyCount,
        int reactionCount,
        ReactionType currentUserReaction,
        boolean isEdited,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {
}

