package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.SearchIndexType;
import com.bondhub.searchservice.service.index.core.SearchIndexSynchronizer;
import com.bondhub.searchservice.service.index.admin.SearchIndexOrchestrator;
import com.bondhub.searchservice.service.index.admin.ElasticsearchAdminService;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search/elasticsearch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ElasticsearchAdminController {

    private final SearchIndexOrchestrator orchestrator;
    private final ElasticsearchAdminService elasticsearchAdminService;
    private final LocalizationUtil localizationUtil;

    @PostMapping("/reindex/{type}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindex(@PathVariable SearchIndexType type) {
        String taskId = orchestrator.reindex(type);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.re-index.success"),
            "taskId", taskId,
            "type", type
        )));
    }

    @GetMapping("/reindex/{type}/status/{taskId}")
    public ResponseEntity<ApiResponse<ReindexStatusResponse>> getReindexStatus(
            @PathVariable SearchIndexType type,
            @PathVariable String taskId) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getStatus(type, taskId)));
    }

    @GetMapping("/index/{type}/summary")
    public ResponseEntity<ApiResponse<ElasticsearchSummaryResponse>> getIndexSummary(@PathVariable SearchIndexType type) {
        SearchIndexSynchronizer handler = orchestrator.getHandler(type);
        return ResponseEntity.ok(ApiResponse.success(ElasticsearchSummaryResponse.builder()

                .health(handler.getHealth())
                .stats(handler.getStats())
                .compare(handler.compareWithDatabase())
                .build()));
    }

    @GetMapping("/index/{type}/stats")
    public ResponseEntity<ApiResponse<IndexStatsResponse>> getIndexStats(@PathVariable SearchIndexType type) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getHandler(type).getStats()));
    }

    @GetMapping("/index/{type}/physical-indexes")
    public ResponseEntity<ApiResponse<List<IndexDetailResponse>>> getPhysicalIndexes(@PathVariable SearchIndexType type) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getHandler(type).getAllPhysicalIndexes()));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<ElasticsearchHealthResponse>> getGlobalHealth() {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.getHealth()));
    }

    @PostMapping("/reindex/users/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> reindexUser(@PathVariable String userId) {
        elasticsearchAdminService.reindexUser(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.re-index.user.success"),
            "userId", userId
        )));
    }

    @GetMapping("/index/{type}/document/{id}")
    public ResponseEntity<ApiResponse<Object>> getDocument(
            @PathVariable SearchIndexType type,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getDocument(type, id)));
    }

    @PostMapping("/index/{type}/switch-alias/{indexName}")
    public ResponseEntity<ApiResponse<IndexOperationResponse>> switchAlias(
            @PathVariable SearchIndexType type,
            @PathVariable String indexName) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.switchAlias(type, indexName)));
    }

    @DeleteMapping("/indexes/{indexName}")
    public ResponseEntity<ApiResponse<IndexOperationResponse>> deleteIndex(@PathVariable String indexName) {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.deletePhysicalIndex(indexName)));
    }
}
