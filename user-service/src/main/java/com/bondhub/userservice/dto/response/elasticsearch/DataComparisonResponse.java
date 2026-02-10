package com.bondhub.userservice.dto.response.elasticsearch;

import com.bondhub.userservice.enums.DataSyncStatus;
import lombok.Builder;

@Builder
public record DataComparisonResponse(
        long elasticsearchCount,
        long databaseCount,
        long difference,
        DataSyncStatus status,  
        String recommendation
) {}
