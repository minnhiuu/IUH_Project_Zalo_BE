package com.bondhub.searchservice.service.index.admin;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.searchservice.dto.response.IndexOperationResponse;
import com.bondhub.searchservice.dto.response.ReindexStatusResponse;
import com.bondhub.searchservice.enums.SearchIndexType;
import com.bondhub.searchservice.service.index.core.SearchIndexMonitor;
import com.bondhub.searchservice.service.index.core.SearchIndexSynchronizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SearchIndexOrchestrator {

    private final Map<SearchIndexType, SearchIndexSynchronizer> syncMap;
    private final Map<SearchIndexType, SearchIndexMonitor> monitorMap;

    public SearchIndexOrchestrator(List<SearchIndexSynchronizer> syncHandlers, List<SearchIndexMonitor> monitorHandlers) {
        this.syncMap = syncHandlers.stream()
                .collect(Collectors.toMap(SearchIndexSynchronizer::getType, Function.identity()));
        this.monitorMap = monitorHandlers.stream()
                .collect(Collectors.toMap(SearchIndexMonitor::getType, Function.identity()));
    }

    public SearchIndexSynchronizer getSyncHandler(SearchIndexType type) {
        SearchIndexSynchronizer handler = syncMap.get(type);
        if (handler == null) {
            throw new AppException(ErrorCode.INVALID_TYPE);
        }
        return handler;
    }

    public SearchIndexMonitor getMonitorHandler(SearchIndexType type) {
        SearchIndexMonitor handler = monitorMap.get(type);
        if (handler == null) {
            throw new AppException(ErrorCode.INVALID_TYPE);
        }
        return handler;
    }

    public String reindex(SearchIndexType type) {
        return getSyncHandler(type).reindexAll();
    }

    public ReindexStatusResponse getStatus(SearchIndexType type, String taskId) {
        return getSyncHandler(type).getReindexStatus(taskId);
    }

    public Object getDocument(SearchIndexType type, String id) {
        return getSyncHandler(type).getDocument(id);
    }

    public IndexOperationResponse switchAlias(SearchIndexType type, String indexName) {
        return getSyncHandler(type).switchAlias(indexName);
    }
}
