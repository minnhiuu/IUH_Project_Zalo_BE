package com.bondhub.searchservice.service;

import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;

import java.util.List;

public interface ElasticsearchAdminService {

    ElasticsearchHealthResponse getHealth();

    IndexStatsResponse getIndexStats();

    DataComparisonResponse compareWithDatabase();

    ElasticsearchSummaryResponse getSummary();

    UserIndex getDocument(String userId);

    void reindexUser(String userId);

    List<IndexDetailResponse> getAllUserIndexes();

    IndexOperationResponse switchAlias(String targetIndexName);

    IndexOperationResponse deletePhysicalIndex(String indexName);
}
