package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;
import com.bondhub.searchservice.service.ElasticsearchAdminService;
import com.bondhub.searchservice.service.UserSyncService;
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

    private final UserSyncService userSyncService;
    private final ElasticsearchAdminService elasticsearchAdminService;
    private final LocalizationUtil localizationUtil;

    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindexAll() {
        String taskId = userSyncService.reindexAll();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.re-index.success"),
            "taskId", taskId
        )));
    }

    @GetMapping("/reindex/status/{taskId}")
    public ResponseEntity<ApiResponse<ReindexStatusResponse>> getReindexStatus(@PathVariable String taskId) {
        return ResponseEntity.ok(ApiResponse.success(userSyncService.getReindexStatus(taskId)));
    }

    @PostMapping("/reindex/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> reindexUser(@PathVariable String userId) {
        elasticsearchAdminService.reindexUser(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.re-index.user.success"),
            "userId", userId
        )));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ElasticsearchSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.getSummary()));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<ElasticsearchHealthResponse>> getHealth() {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.getHealth()));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<IndexStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.getIndexStats()));
    }

    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<DataComparisonResponse>> compareWithDatabase() {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.compareWithDatabase()));
    }

    @GetMapping("/document/{userId}")
    public ResponseEntity<ApiResponse<UserIndex>> getDocument(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.getDocument(userId)));
    }

    @GetMapping("/indexes")
    public ResponseEntity<ApiResponse<List<IndexDetailResponse>>> getAllIndexes() {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.getAllUserIndexes()));
    }

    @PostMapping("/indexes/{indexName}/switch")
    public ResponseEntity<ApiResponse<IndexOperationResponse>> switchAlias(@PathVariable String indexName) {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.switchAlias(indexName)));
    }

    @DeleteMapping("/indexes/{indexName}")
    public ResponseEntity<ApiResponse<IndexOperationResponse>> deleteIndex(@PathVariable String indexName) {
        return ResponseEntity.ok(ApiResponse.success(elasticsearchAdminService.deletePhysicalIndex(indexName)));
    }
}
