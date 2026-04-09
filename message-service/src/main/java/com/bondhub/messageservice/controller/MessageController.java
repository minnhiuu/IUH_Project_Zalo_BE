package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.service.message.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping
@Tag(name = "Message", description = "Message REST API")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a message to a conversation")
    public ResponseEntity<ApiResponse<Void>> sendMessage(
            @PathVariable String conversationId,
            @RequestBody MessageSendRequest request) {
        messageService.sendMessage(conversationId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Get messages of a conversation with pagination")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> getChatMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findChatMessages(conversationId, page, size)));
    }

    @PatchMapping("/messages/{messageId}/revoke")
    @Operation(summary = "Revoke a message (sender only)")
    public ResponseEntity<ApiResponse<Void>> revokeMessage(@PathVariable String messageId) {
        messageService.revokeMessage(messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/messages/{messageId}/me")
    @Operation(summary = "Delete a message for current user only")
    public ResponseEntity<ApiResponse<Void>> deleteMessageForMe(@PathVariable String messageId) {
        messageService.deleteMessageForMe(messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}