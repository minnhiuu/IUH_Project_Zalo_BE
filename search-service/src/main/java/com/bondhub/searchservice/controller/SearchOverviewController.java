package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.dto.request.MessageSearchRequest;
import com.bondhub.searchservice.dto.response.MessageSearchOverviewResponse;
import com.bondhub.searchservice.service.index.message.MessageSearchService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchOverviewController {

    MessageSearchService messageSearchService;
    SecurityUtil securityUtil;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<MessageSearchOverviewResponse>> getSearchOverview(
            @Valid @ModelAttribute MessageSearchRequest request,
            @RequestParam(defaultValue = "5") int sectionSize) {
        return ResponseEntity.ok(ApiResponse.success(
                messageSearchService.searchMessageOverview(
                        securityUtil.getCurrentUserId(),
                        request,
                        sectionSize)));
    }
}
