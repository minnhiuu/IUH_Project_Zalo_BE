package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.SearchMemberResponse;
import com.bondhub.messageservice.service.conversation.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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

    @PostMapping("/groups")
    @Operation(summary = "Create a conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> createGroupConversation(
            @RequestBody @Valid GroupConversationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.createGroupConversation(request)));
    }

    @PatchMapping("/{conversationId}/name")
    @Operation(summary = "Update group conversation name")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateGroupName(
            @PathVariable String conversationId,
            @RequestParam String name) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.updateGroupName(conversationId, name)));
    }

    @PatchMapping(value = "/{conversationId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update group conversation avatar")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateGroupAvatar(
            @PathVariable String conversationId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.updateGroupAvatar(conversationId, file)));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Delete conversation only for current user")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable String conversationId) {
        conversationService.deleteConversationForMe(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{conversationId}/groups")
    @Operation(summary = "Disband group conversation (Owner only)")
    public ResponseEntity<ApiResponse<Void>> disbandGroup(@PathVariable String conversationId) {
        conversationService.disbandGroup(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/friends-directory")
    @Operation(summary = "Get initial friends list grouped by alphabet (A-Z)")
    public ResponseEntity<ApiResponse<Map<String, List<SearchMemberResponse>>>> getFriendsDirectory(
            @RequestParam(required = false) String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getFriendsDirectory(conversationId)));
    }


    @GetMapping("/search-members")
    @Operation(summary = "Search friends (by name) or strangers (by phone) to add to group")
    public ResponseEntity<ApiResponse<PageResponse<List<SearchMemberResponse>>>> searchMembersToAdd(
            @RequestParam(required = false) String conversationId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.searchMembersToAdd(conversationId, query, page, size)));
    }
}