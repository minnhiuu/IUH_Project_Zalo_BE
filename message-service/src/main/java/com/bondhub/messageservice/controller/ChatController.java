package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.service.ChatMessageService;
import com.bondhub.messageservice.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
@Tag(name = "Chat", description = "Real-time chat API")
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;

    @MessageMapping("/chat")
    public void processMessage(@Payload Message message, Principal principal) {
        message.setSenderId(principal.getName());
        chatMessageService.sendMessage(message);
    }

    @GetMapping("/{recipientId}")
    @Operation(summary = "Get chat messages by recipient ID")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> findChatMessages(
            @PathVariable String recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.findChatMessages(recipientId, page, size)));
    }

    @GetMapping("/conversations")
    @Operation(summary = "Get all chat rooms/conversations for the current user")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationResponse>>>> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                chatRoomService.getUserConversations(page, size)));
    }
}
