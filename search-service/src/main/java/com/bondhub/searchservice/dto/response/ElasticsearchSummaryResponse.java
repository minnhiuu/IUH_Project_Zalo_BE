package com.bondhub.searchservice.dto.response;

import lombok.Builder;

@Builder
public record ElasticsearchSummaryResponse(
        ElasticsearchHealthResponse health,
        IndexStatsResponse stats,
        DataComparisonResponse compare,
        long failedEventsCount
) {}
