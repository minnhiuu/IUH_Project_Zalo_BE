package com.bondhub.searchservice.dto.response;

import lombok.Builder;

@Builder
public record UserInteractionFeatureRebuildResponse(
        int sinceDays,
        int sourceLimit,
        int chatSnapshotCount,
        int socialSnapshotCount,
        int upsertedFeatureCount,
        long tookMs
) {
}
