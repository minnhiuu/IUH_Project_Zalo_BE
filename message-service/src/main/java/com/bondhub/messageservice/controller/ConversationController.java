package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
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
@Tag(name = "Conversation", description = "Room-centric Chat REST API (ObjectId-based)")
public class ConversationController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    // ─── Gửi tin nhắn ────────────────────────────────────────────────────────

    @PostMapping("/send")
    @Operation(summary = "Gửi tin nhắn vào phòng chat. Delivery via Kafka → socket-service → WebSocket.")
    public ResponseEntity<ApiResponse<Void>> sendMessage(@RequestBody MessageSendRequest request) {
        messageService.sendMessage(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ─── Lấy tin nhắn theo conversationId (ObjectId) ─────────────────────────

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Lấy tin nhắn của phòng chat theo conversationId. Kiểm tra quyền thành viên.")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> getChatMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findChatMessages(conversationId, page, size)));
    }

    // ─── Danh sách phòng chat ─────────────────────────────────────────────────

    @GetMapping("/conversations")
    @Operation(summary = "Lấy danh sách phòng chat của currentUser (phân trang, sắp xếp theo lastMessage)")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationResponse>>>> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getUserConversations(page, size)));
    }

    // ─── Get-or-create phòng chat 1-1 với partner ────────────────────────────

    @GetMapping("/conversations/partner/{partnerId}")
    @Operation(summary = "Lấy hoặc tạo phòng chat 1-1 với partner. Frontend gọi trước khi gửi tin nhắn mới.")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateConversationWithPartner(
            @PathVariable String partnerId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getOrCreateConversationForUser(partnerId)));
    }

    // ─── Đọc / Read-receipt ───────────────────────────────────────────────────

    @PutMapping("/conversations/{conversationId}/read")
    @Operation(summary = "Đánh dấu đã đọc conversation. Kiểm tra quyền thành viên.")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String conversationId) {
        conversationService.markAsRead(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ─── Thu hồi / Xóa ───────────────────────────────────────────────────────

    @PatchMapping("/{id}/revoke")
    @Operation(summary = "Thu hồi tin nhắn (chỉ người gửi)")
    public ResponseEntity<ApiResponse<Void>> revokeMessage(@PathVariable String id) {
        messageService.revokeMessage(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/me/{id}")
    @Operation(summary = "Xóa tin nhắn chỉ phía mình")
    public ResponseEntity<ApiResponse<Void>> deleteMessageForMe(@PathVariable String id) {
        messageService.deleteMessageForMe(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
