package com.bondhub.searchservice.dto.response;

import lombok.Builder;

@Builder
public record IndexStatsResponse(
        String indexName,
        long documentCount,
        String primaryStoreSize,
        String totalStoreSize,
        int numberOfShards,
        int numberOfReplicas
) {}
