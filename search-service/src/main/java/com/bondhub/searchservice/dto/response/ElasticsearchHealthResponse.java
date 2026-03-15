package com.bondhub.searchservice.dto.response;

import com.bondhub.searchservice.enums.ElasticsearchClusterStatus;
import lombok.Builder;

@Builder
public record ElasticsearchHealthResponse(
        ElasticsearchClusterStatus status,
        String clusterName,
        boolean indexExists,
        String currentIndexName,
        String aliasName
) {}
