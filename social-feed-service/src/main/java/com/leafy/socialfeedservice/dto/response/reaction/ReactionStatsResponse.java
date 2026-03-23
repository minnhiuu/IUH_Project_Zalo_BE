package com.leafy.socialfeedservice.dto.response.reaction;

import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import com.leafy.socialfeedservice.model.enums.ReactionType;
import lombok.Builder;

import java.util.Map;

@Builder
public record ReactionStatsResponse(
        String targetId,
        ReactionTargetType targetType,
        long totalReactions,
        Map<ReactionType, Long> countsByType
) {
}
