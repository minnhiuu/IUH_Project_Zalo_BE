package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.userservice.dto.response.SearchSyncResponse;
import com.bondhub.userservice.service.user.UserSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/admin/search")
@RequiredArgsConstructor
@Slf4j
public class SearchManagementController {

    private final UserSearchService userSearchService;
    private final LocalizationUtil localizationUtil;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/re-index")
    public ResponseEntity<ApiResponse<SearchSyncResponse>> reIndexAll() {
        log.info("Admin Request: Full Search Re-indexing for Users");
        long count = userSearchService.reIndexAll();
        return ResponseEntity.ok(ApiResponse.success(
                SearchSyncResponse.builder()
                        .message(localizationUtil.getMessage("search.re-index.success"))
                        .totalSynced(count)
                        .indexName("users")
                        .build()
        ));
    }
}
