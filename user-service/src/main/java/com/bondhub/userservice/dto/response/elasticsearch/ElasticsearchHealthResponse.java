package com.bondhub.userservice.dto.response.elasticsearch;

import com.bondhub.userservice.enums.ElasticsearchClusterStatus;
import lombok.Builder;

@Builder
public record ElasticsearchHealthResponse(
        ElasticsearchClusterStatus status,          
        String clusterName,
        boolean indexExists,
        String currentIndexName, 
        String aliasName
) {}
