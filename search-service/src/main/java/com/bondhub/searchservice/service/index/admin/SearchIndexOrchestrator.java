package com.bondhub.searchservice.service.index.admin;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.searchservice.dto.response.ReindexStatusResponse;
import com.bondhub.searchservice.enums.SearchIndexType;
import com.bondhub.searchservice.service.index.core.SearchIndexSynchronizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SearchIndexOrchestrator {

    private final Map<SearchIndexType, SearchIndexSynchronizer> handlerMap;

    public SearchIndexOrchestrator(List<SearchIndexSynchronizer> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(SearchIndexSynchronizer::getType, Function.identity()));
    }

    public SearchIndexSynchronizer getHandler(SearchIndexType type) {
        SearchIndexSynchronizer handler = handlerMap.get(type);
        if (handler == null) {
            throw new AppException(ErrorCode.INVALID_TYPE);
        }
        return handler;
    }

    public String reindex(SearchIndexType type) {
        return getHandler(type).reindexAll();
    }

    public ReindexStatusResponse getStatus(SearchIndexType type, String taskId) {
        return getHandler(type).getReindexStatus(taskId);
    }
}
