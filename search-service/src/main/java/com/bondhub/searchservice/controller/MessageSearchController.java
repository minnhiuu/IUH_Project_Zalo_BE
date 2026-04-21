package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.dto.request.MessageSearchRequest;
import com.bondhub.searchservice.dto.response.MessageSearchOverviewResponse;
import com.bondhub.searchservice.dto.response.MessageSearchResponse;
import com.bondhub.searchservice.enums.MessageSearchSection;
import com.bondhub.searchservice.service.message.MessageSearchService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search/messages")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageSearchController {

    MessageSearchService messageSearchService;
    SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<List<MessageSearchResponse>>>> searchMessages(
            @Valid @ModelAttribute MessageSearchRequest request,
            @RequestParam(defaultValue = "all") MessageSearchSection section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                messageSearchService.searchMessages(securityUtil.getCurrentUserId(), request, section, pageable)));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<MessageSearchOverviewResponse>> getMessageSearchOverview(
            @Valid @ModelAttribute MessageSearchRequest request,
            @RequestParam(defaultValue = "5") int sectionSize) {
        return ResponseEntity.ok(ApiResponse.success(
                messageSearchService.searchMessageOverview(
                        securityUtil.getCurrentUserId(),
                        request,
                        sectionSize)));
    }
}
