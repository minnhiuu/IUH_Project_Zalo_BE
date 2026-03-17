package com.leafy.socialfeedservice.dto.response.reaction;

import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import com.leafy.socialfeedservice.model.enums.ReactionType;
import lombok.Builder;

@Builder
public record ReactionResponse(
        String id,
        String targetId,
        ReactionTargetType targetType,
        ReactionType type,
        boolean active,
        long totalReactions
) {
}
