package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.SearchIndexType;
import com.bondhub.searchservice.service.interactionfeature.UserInteractionFeatureRebuildService;
import com.bondhub.searchservice.service.index.core.SearchIndexMonitor;
import com.bondhub.searchservice.service.index.core.SearchIndexSynchronizer;
import com.bondhub.searchservice.service.index.admin.SearchIndexOrchestrator;
import com.bondhub.searchservice.service.index.admin.ElasticsearchAdminService;

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
    private final UserInteractionFeatureRebuildService userInteractionFeatureRebuildService;
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
        SearchIndexSynchronizer syncHandler = orchestrator.getSyncHandler(type);
        SearchIndexMonitor monitorHandler = orchestrator.getMonitorHandler(type);
        
        return ResponseEntity.ok(ApiResponse.success(ElasticsearchSummaryResponse.builder()
                .health(monitorHandler.getHealth())
                .stats(monitorHandler.getStats())
                .compare(syncHandler.compareWithDatabase())
                .failedEventsCount(monitorHandler.getFailedEventsCount())
                .build()));
    }

    @GetMapping("/index/{type}/failed-events")
    public ResponseEntity<ApiResponse<PageResponse<List<FailedEventResponse>>>> getFailedEvents(
            @PathVariable SearchIndexType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getMonitorHandler(type).getFailedEvents(page, size)));
    }

    @GetMapping("/index/{type}/stats")
    public ResponseEntity<ApiResponse<IndexStatsResponse>> getIndexStats(@PathVariable SearchIndexType type) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getMonitorHandler(type).getStats()));
    }

    @GetMapping("/index/{type}/physical-indexes")
    public ResponseEntity<ApiResponse<List<IndexDetailResponse>>> getPhysicalIndexes(@PathVariable SearchIndexType type) {
        return ResponseEntity.ok(ApiResponse.success(orchestrator.getMonitorHandler(type).getAllPhysicalIndexes()));
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

    @PostMapping("/interaction-features/rebuild")
    public ResponseEntity<ApiResponse<UserInteractionFeatureRebuildResponse>> rebuildInteractionFeatures(
            @RequestParam(defaultValue = "30") int sinceDays,
            @RequestParam(defaultValue = "5000") int sourceLimit) {
        return ResponseEntity.ok(ApiResponse.success(
                userInteractionFeatureRebuildService.rebuild(sinceDays, sourceLimit)));
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
