package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.service.index.message.MessageSearchService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search/contacts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ContactSearchController {

    MessageSearchService messageSearchService;
    SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationSearchResponse>>>> searchContacts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                messageSearchService.searchConversations(securityUtil.getCurrentUserId(), keyword, isGroup, pageable)));
    }
}
