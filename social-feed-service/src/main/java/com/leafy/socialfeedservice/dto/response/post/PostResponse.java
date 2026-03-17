package com.leafy.socialfeedservice.dto.response.post;

import com.leafy.socialfeedservice.model.embedded.PostContent;
import com.leafy.socialfeedservice.model.embedded.PostMedia;
import com.leafy.socialfeedservice.model.embedded.PostStats;
import com.leafy.socialfeedservice.model.enums.PostType;
import com.leafy.socialfeedservice.model.enums.Visibility;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PostResponse(
        String id,
        String authorId,
        String groupId,
        PostContent content,
        List<PostMedia> media,
        PostType postType,
        Visibility visibility,
        PostStats stats,
        LocalDateTime uploadedAt,
        LocalDateTime updatedAt,
        int version,
        boolean isCurrent,
        boolean isEdited
) {
}
