package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.searchservice.dto.request.RecentSearchRequest;
import com.bondhub.searchservice.dto.response.RecentHistoryResponse;
import com.bondhub.searchservice.dto.response.RecentSearchResponse;
import com.bondhub.searchservice.enums.SearchType;
import com.bondhub.searchservice.service.RecentSearchService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search/recent")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecentSearchController {

    RecentSearchService recentSearchService;

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<RecentSearchResponse>>> getRecentItems() {
        return ResponseEntity.ok(ApiResponse.success(recentSearchService.getRecentItems()));
    }
 
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<RecentHistoryResponse>> getRecentHistory() {
        return ResponseEntity.ok(ApiResponse.success(recentSearchService.getRecentHistory()));
    }

    @GetMapping("/queries")
    public ResponseEntity<ApiResponse<List<RecentSearchResponse>>> getRecentQueries() {
        return ResponseEntity.ok(ApiResponse.success(recentSearchService.getRecentQueries()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addSearchItem(@Valid @RequestBody RecentSearchRequest request) {
        recentSearchService.addSearchItem(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> removeItem(
            @PathVariable String id,
            @RequestParam SearchType type) {
        recentSearchService.removeItem(id, type);
        return ApiResponse.successWithoutData();
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<ApiResponse<Void>> clearAll() {
        recentSearchService.clearAllHistory();
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
