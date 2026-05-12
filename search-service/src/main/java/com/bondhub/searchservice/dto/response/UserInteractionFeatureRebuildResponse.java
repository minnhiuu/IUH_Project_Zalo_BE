package com.bondhub.searchservice.dto.response;

import lombok.Builder;

@Builder
public record UserInteractionFeatureRebuildResponse(
        int sinceDays,
        int sourceLimit,
        int chatSnapshotCount,
        int socialSnapshotCount,
        int processedSnapshotCount,
        int uniqueFeatureCount,
        int upsertedFeatureCount,
        long tookMs
) {
}
