package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;
import com.bondhub.searchservice.dto.request.MessageSearchRequest;
import com.bondhub.searchservice.dto.response.MessageNavigationResponse;
import com.bondhub.searchservice.dto.response.MessageSearchGroupResponse;
import com.bondhub.searchservice.dto.response.MessageSearchResponse;
import com.bondhub.searchservice.enums.MessageSearchSection;
import com.bondhub.searchservice.service.index.message.MessageSearchService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageSearchGroupResponse>>>> searchMessageGroups(
            @Valid @ModelAttribute MessageSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                messageSearchService.searchMessageGroups(securityUtil.getCurrentUserId(), request, pageable)));
    }

    @GetMapping("/senders")
    public ResponseEntity<ApiResponse<List<ConversationSearchResponse>>> getMessageSearchSenders(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(
                messageSearchService.searchMessageSenders(
                        securityUtil.getCurrentUserId(),
                        keyword)));
    }

    @GetMapping("/navigation")
    public ResponseEntity<ApiResponse<MessageNavigationResponse>> navigateSearchResult(
            @RequestParam(required = false) String keyword,
            @RequestParam String conversationId,
            @RequestParam(required = false) String senderId,
            @RequestParam(required = false) String currentMessageId,
            @RequestParam(defaultValue = "NEXT") String direction,
            @RequestParam(defaultValue = "all") MessageSearchSection section) {
        String userId = securityUtil.getCurrentUserId();
        log.info("GET /search/messages/navigation userId={}, conversationId={}, keyword={}, senderId={}, currentMessageId={}, direction={}, section={}",
                userId, conversationId, keyword, senderId, currentMessageId, direction, section);
        return ResponseEntity.ok(ApiResponse.success(
                messageSearchService.navigateSearchResult(
                        userId,
                        conversationId,
                        keyword,
                        senderId,
                        currentMessageId,
                        direction,
                        section)));
    }
}
