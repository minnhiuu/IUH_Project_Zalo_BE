package com.bondhub.searchservice.service.index.core;

import com.bondhub.searchservice.dto.response.ElasticsearchHealthResponse;
import com.bondhub.searchservice.dto.response.IndexDetailResponse;
import com.bondhub.searchservice.dto.response.IndexStatsResponse;
import com.bondhub.searchservice.enums.SearchIndexType;

import java.util.List;

public interface SearchIndexMonitor {
    SearchIndexType getType();
    String getAlias();
    Class<?> getIndexClass();
    
    ElasticsearchHealthResponse getHealth();
    IndexStatsResponse getStats();
    List<IndexDetailResponse> getAllPhysicalIndexes();
}
