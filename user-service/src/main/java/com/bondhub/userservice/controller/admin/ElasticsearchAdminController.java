package com.bondhub.userservice.controller.admin;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.userservice.dto.request.elasticsearch.AnalyzeRequest;
import com.bondhub.userservice.dto.response.elasticsearch.*;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.service.elasticsearch.ElasticsearchAdminService;
import com.bondhub.userservice.service.elasticsearch.UserSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users/elasticsearch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ElasticsearchAdminController {

    private final UserSyncService userSyncService;
    private final ElasticsearchAdminService elasticsearchAdminService;
    private final LocalizationUtil localizationUtil;

    @PostMapping("/reindex")
    public ApiResponse<Map<String, Object>> reindexAll() {
        String taskId = userSyncService.reindexAll();
        
        return ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.re-index.success"),
            "taskId", taskId
        ));
    }

    @GetMapping("/reindex/status/{taskId}")
    public ApiResponse<ReindexStatusResponse> getReindexStatus(@PathVariable String taskId) {
        return ApiResponse.success(userSyncService.getReindexStatus(taskId));
    }

    @PostMapping("/reindex/{userId}")
    public ApiResponse<Map<String, String>> reindexUser(@PathVariable String userId) {
        elasticsearchAdminService.reindexUser(userId);
        return ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.re-index.user.success"),
            "userId", userId
        ));
    }

    @GetMapping("/health")
    public ApiResponse<ElasticsearchHealthResponse> getHealth() {
        return ApiResponse.success(elasticsearchAdminService.getHealth());
    }

    @GetMapping("/stats")
    public ApiResponse<IndexStatsResponse> getStats() {
        return ApiResponse.success(elasticsearchAdminService.getIndexStats());
    }

    @GetMapping("/compare")
    public ApiResponse<DataComparisonResponse> compareWithDatabase() {
        return ApiResponse.success(elasticsearchAdminService.compareWithDatabase());
    }

    @GetMapping("/document/{userId}")
    public ApiResponse<UserIndex> getDocument(@PathVariable String userId) {
        return ApiResponse.success(elasticsearchAdminService.getDocument(userId));
    }

    @GetMapping("/indexes")
    public ApiResponse<List<IndexDetailResponse>> getAllIndexes() {
        return ApiResponse.success(elasticsearchAdminService.getAllUserIndexes());
    }

    @PostMapping("/indexes/{indexName}/switch")
    public ApiResponse<IndexOperationResponse> switchAlias(@PathVariable String indexName) {
        return ApiResponse.success(elasticsearchAdminService.switchAlias(indexName));
    }

    @DeleteMapping("/indexes/{indexName}")
    public ApiResponse<IndexOperationResponse> deleteIndex(@PathVariable String indexName) {
        return ApiResponse.success(elasticsearchAdminService.deletePhysicalIndex(indexName));
    }
}
