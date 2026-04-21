package com.bondhub.searchservice.service.index.core;

import com.bondhub.searchservice.dto.response.DataComparisonResponse;
import com.bondhub.searchservice.dto.response.IndexOperationResponse;
import com.bondhub.searchservice.dto.response.ReindexStatusResponse;
import com.bondhub.searchservice.enums.SearchIndexType;

public interface SearchIndexSynchronizer {
    SearchIndexType getType();
    String getAlias();
    Class<?> getIndexClass();
    String reindexAll();
    ReindexStatusResponse getReindexStatus(String taskId);
    DataComparisonResponse compareWithDatabase();
    Object getDocument(String id);
    IndexOperationResponse switchAlias(String targetIndexName);
}
