package com.bondhub.userservice.controller.admin;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.userservice.service.elasticsearch.UserSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users/elasticsearch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ElasticsearchAdminController {

    private final UserSyncService userSyncService;
    private final LocalizationUtil localizationUtil;

    @PostMapping("/reindex")
    public ApiResponse<Map<String, Object>> reindexAll() {
        long totalPublished = userSyncService.reindexAll();
        
        return ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.re-index.success"),
            "totalPublished", totalPublished
        ));
    }
}
