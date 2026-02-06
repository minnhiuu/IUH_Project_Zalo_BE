package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.userservice.dto.response.SearchSyncResponse;
import com.bondhub.userservice.service.user.UserSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/search")
@RequiredArgsConstructor
@Slf4j
public class UserSearchController {

    private final UserSearchService userSearchService;
    private final LocalizationUtil localizationUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<List<UserSummaryResponse>>>> searchUsers(
            @RequestParam String keyword,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Request: Search users with keyword: {}", keyword);
        return ResponseEntity.ok(ApiResponse.success(userSearchService.searchUsers(keyword, pageable)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/re-index")
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
