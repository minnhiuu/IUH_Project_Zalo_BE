package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.userservice.dto.request.elasticsearch.AnalyzeRequest;
import com.bondhub.userservice.dto.response.elasticsearch.*;
import com.bondhub.userservice.model.elasticsearch.UserIndex;

import java.util.List;
import java.util.Map;

public interface ElasticsearchAdminService {

    ElasticsearchHealthResponse getHealth();

    IndexStatsResponse getIndexStats();

    DataComparisonResponse compareWithDatabase();

    UserIndex getDocument(String userId);

    void reindexUser(String userId);

    List<IndexDetailResponse> getAllUserIndexes();

    IndexOperationResponse switchAlias(String targetIndexName);

    IndexOperationResponse deletePhysicalIndex(String indexName);
}
