package com.bondhub.socialfeedservice.dto.response.reaction;

import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import lombok.Builder;

@Builder
public record ReactionResponse(
        String id,
        AuthorInfo authorInfo,
        String targetId,
        ReactionTargetType targetType,
        ReactionType type,
        boolean active,
        long totalReactions
) {
}
