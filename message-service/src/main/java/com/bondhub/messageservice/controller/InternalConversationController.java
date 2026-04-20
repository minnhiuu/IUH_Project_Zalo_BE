package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
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
}
