package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.request.MessageSendRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.service.message.MessageService;
import com.bondhub.messageservice.service.conversation.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
@Tag(name = "Chat", description = "Real-time chat REST API")
public class ChatController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    @PostMapping("/send")
    @Operation(summary = "Send a message (REST). Delivery via Kafka → socket-service → WebSocket.")
    public ResponseEntity<ApiResponse<Void>> sendMessage(@RequestBody MessageSendRequest request) {
        messageService.sendMessage(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{recipientId}")
    @Operation(summary = "Get chat messages by recipient ID")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> findChatMessages(
            @PathVariable String recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findChatMessages(recipientId, page, size)));
    }

    @GetMapping("/conversations")
    @Operation(summary = "Get all chat rooms/conversations for the current user")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationResponse>>>> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getUserConversations(page, size)));
    }

    @PutMapping("/conversations/{chatId}/read")
    @Operation(summary = "Mark a conversation as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String chatId) {
        conversationService.markAsRead(chatId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{id}/revoke")
    @Operation(summary = "Revoke a message for everyone (sender only)")
    public ResponseEntity<ApiResponse<Void>> revokeMessage(@PathVariable String id) {
        messageService.revokeMessage(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/me/{id}")
    @Operation(summary = "Delete a message only for the current user")
    public ResponseEntity<ApiResponse<Void>> deleteMessageForMe(@PathVariable String id) {
        messageService.deleteMessageForMe(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
