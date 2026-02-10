package com.bondhub.userservice.dto.response.elasticsearch;

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
