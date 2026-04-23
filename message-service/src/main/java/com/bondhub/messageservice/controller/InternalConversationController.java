package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.messageservice.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Internal endpoints for inter-service communication (not exposed to API Gateway)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/conversations")
public class InternalConversationController {

    private final ConversationService conversationService;

    @GetMapping("/{conversationId}/member-ids")
    public ResponseEntity<ApiResponse<Set<String>>> getMemberIds(@PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getConversationMemberIds(conversationId)));
    }
}
