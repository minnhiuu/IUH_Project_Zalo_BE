package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.messageservice.dto.request.ReactionRequest;
import com.bondhub.messageservice.dto.response.MessageContextResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.dto.response.MessageSeenResponse;
import com.bondhub.messageservice.service.message.MessageService;
import jakarta.validation.Valid;
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

    @GetMapping("/conversations/{conversationId}/media")
    @Operation(summary = "Get messages filtered by type (IMAGE, VIDEO, FILE, LINK)")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> getMediaMessages(
            @PathVariable String conversationId,
            @RequestParam List<String> types,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findMediaMessages(conversationId, types, page, size)));
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

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}/admin")
    @Operation(summary = "Delete a member's message in group (Admin/Owner only; Admin cannot delete Owner's message)")
    public ResponseEntity<ApiResponse<Void>> deleteGroupMemberMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        messageService.deleteGroupMemberMessage(conversationId, messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/messages/{messageId}/reactions")
    @Operation(summary = "Toggle reaction on a message (add if not present, remove if already reacted)")
    public ResponseEntity<ApiResponse<Void>> toggleReaction(
            @PathVariable String messageId,
            @Valid @RequestBody ReactionRequest request) {
        messageService.toggleReaction(messageId, request.emoji());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/messages/{messageId}/reactions/me")
    @Operation(summary = "Remove all reactions of current user from a message")
    public ResponseEntity<ApiResponse<Void>> removeAllMyReactions(@PathVariable String messageId) {
        messageService.removeAllMyReactions(messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/seen-members")
    @Operation(summary = "Get members who have seen a message in a group conversation")
    public ResponseEntity<ApiResponse<List<MessageSeenResponse>>> getSeenMembers(
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.getSeenMembers(conversationId, messageId)));
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/context")
    @Operation(summary = "Get the page index of a specific message (used by FE to scroll-to from search result)")
    public ResponseEntity<ApiResponse<MessageContextResponse>> getMessageContext(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.getMessageContext(conversationId, messageId, size)));
    }
}