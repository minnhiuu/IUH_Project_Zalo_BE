package com.bondhub.searchservice.service.index.core;

import com.bondhub.searchservice.dto.response.DataComparisonResponse;
import com.bondhub.searchservice.dto.response.IndexOperationResponse;
import com.bondhub.searchservice.dto.response.ReindexStatusResponse;

public interface SearchIndexSynchronizer extends SearchIndexMonitor {
    String reindexAll();
    ReindexStatusResponse getReindexStatus(String taskId);
    DataComparisonResponse compareWithDatabase();
    Object getDocument(String id);
    IndexOperationResponse switchAlias(String targetIndexName);
}
