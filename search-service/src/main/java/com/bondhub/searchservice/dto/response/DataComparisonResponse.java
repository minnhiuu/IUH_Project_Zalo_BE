package com.bondhub.searchservice.dto.response;

import com.bondhub.searchservice.enums.DataSyncStatus;
import lombok.Builder;

@Builder
public record DataComparisonResponse(
        long elasticsearchCount,
        long databaseCount,
        long difference,
        DataSyncStatus status,
        String recommendation
) {}
