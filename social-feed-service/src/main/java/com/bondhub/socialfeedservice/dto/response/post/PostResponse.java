package com.bondhub.socialfeedservice.dto.response.post;

import com.bondhub.socialfeedservice.model.embedded.PostContent;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import com.bondhub.socialfeedservice.model.embedded.PostMusic;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
import com.bondhub.socialfeedservice.model.embedded.LocationInfo;
import com.bondhub.socialfeedservice.model.embedded.StoryElement;
import com.bondhub.socialfeedservice.model.enums.PostType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import com.bondhub.socialfeedservice.model.enums.Visibility;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PostResponse(
        String id,
        AuthorInfo authorInfo,
        String groupId,
        PostContent content,
        List<PostMedia> media,
        PostType postType,
        String sharedPostId,
        SharedPostPreview sharedPostPreview,
        String originalAuthorId,
        PostContent sharedCaption,
        String rootPostId,
        LocalDateTime expiresAt,
        PostMusic music,
        List<String> viewerIds,
        LocationInfo location,
        List<StoryElement> elements,
        Visibility visibility,
        PostStats stats,
        ReactionType currentUserReaction,
        LocalDateTime uploadedAt,
        LocalDateTime updatedAt,
        int version,
        boolean isCurrent,
        boolean isEdited
) {
}
