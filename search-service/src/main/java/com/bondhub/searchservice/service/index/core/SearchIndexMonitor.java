package com.bondhub.searchservice.service.index.core;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.SearchIndexType;

import java.util.List;

public interface SearchIndexMonitor {
    SearchIndexType getType();
    String getAlias();
    Class<?> getIndexClass();
    
    ElasticsearchHealthResponse getHealth();
    IndexStatsResponse getStats();
    List<IndexDetailResponse> getAllPhysicalIndexes();
    
    long getFailedEventsCount();
    PageResponse<List<FailedEventResponse>> getFailedEvents(int page, int size);
}
