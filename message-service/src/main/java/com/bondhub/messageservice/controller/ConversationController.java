package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.service.conversation.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/conversations")
@Tag(name = "Conversation", description = "Conversation REST API")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "Get conversations of current user (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationResponse>>>> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getUserConversations(page, size)));
    }

    @GetMapping("/partner/{partnerId}")
    @Operation(summary = "Get or create a one-to-one conversation with a partner")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateConversationWithPartner(
            @PathVariable String partnerId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getOrCreateConversationForUser(partnerId)));
    }

    @PutMapping("/{conversationId}/read")
    @Operation(summary = "Mark a conversation as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String conversationId) {
        conversationService.markAsRead(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}