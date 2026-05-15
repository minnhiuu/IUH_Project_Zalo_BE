package com.bondhub.searchservice.dto.response;

import lombok.Builder;

@Builder
public record UserSearchScoreBreakdown(
        double esScore,
        double exactPhoneBoost,
        double esScoreBoost,
        double relationshipBoost,
        double graphBoost,
        double contactBoost,
        double recentInteractionBoost,
        double finalScore
) {
}
