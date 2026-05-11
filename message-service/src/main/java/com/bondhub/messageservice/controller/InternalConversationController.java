package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;
import com.bondhub.messageservice.service.conversation.ConversationInternalService;
import com.bondhub.messageservice.service.conversation.ConversationService;
import com.bondhub.messageservice.service.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Internal endpoints for inter-service communication (not exposed to API Gateway)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/conversations")
public class InternalConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ConversationInternalService conversationInternalService;

    @GetMapping("/{conversationId}/member-ids")
    public ResponseEntity<ApiResponse<Set<String>>> getMemberIds(@PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getConversationMemberIds(conversationId)));
    }

    @GetMapping("/{conversationId}/messages-since")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessagesSince(
            @PathVariable String conversationId,
            @RequestParam String sinceId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.getMessagesSince(conversationId, sinceId, userId)));
    }

    @GetMapping("/{conversationId}/members/{userId}")
    public ResponseEntity<ApiResponse<ConversationMemberLookupResponse>> getConversationMember(
            @PathVariable String conversationId,
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationInternalService.getConversationMember(conversationId, userId)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationSearchResponse>>>> searchConversations(
            @RequestParam String userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationInternalService.searchConversations(userId, keyword, isGroup, page, size)));
    }
}
