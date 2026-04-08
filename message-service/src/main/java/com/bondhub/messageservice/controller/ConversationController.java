package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.request.AddMembersRequest;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.GroupMemberListItemResponse;
import com.bondhub.messageservice.dto.response.SearchMemberResponse;
import com.bondhub.messageservice.service.conversation.ConversationService;
import com.bondhub.messageservice.service.conversation.GroupConversationService;
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
        private final GroupConversationService groupConversationService;

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
                groupConversationService.createGroupConversation(request)));
    }

    @PatchMapping("/{conversationId}/name")
    @Operation(summary = "Update group conversation name")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateGroupName(
            @PathVariable String conversationId,
            @RequestParam String name) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.updateGroupName(conversationId, name)));
    }

    @PatchMapping(value = "/{conversationId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update group conversation avatar")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateGroupAvatar(
            @PathVariable String conversationId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.updateGroupAvatar(conversationId, file)));
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
                groupConversationService.disbandGroup(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{conversationId}/leave")
    @Operation(summary = "Leave group conversation")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "false") boolean silent) {
                groupConversationService.leaveGroup(conversationId, silent);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/friends-directory")
    @Operation(summary = "Get initial friends list grouped by alphabet (A-Z)")
    public ResponseEntity<ApiResponse<Map<String, List<SearchMemberResponse>>>> getFriendsDirectory(
            @RequestParam(required = false) String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getFriendsDirectory(conversationId)));
    }


    @GetMapping("/search-members")
    @Operation(summary = "Search friends (by name) or strangers (by phone) to add to group")
    public ResponseEntity<ApiResponse<PageResponse<List<SearchMemberResponse>>>> searchMembersToAdd(
            @RequestParam(required = false) String conversationId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.searchMembersToAdd(conversationId, query, page, size)));
    }

    @PostMapping("/{conversationId}/members")
    @Operation(summary = "Add members to group conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> addMembersToGroup(
            @PathVariable String conversationId,
            @RequestBody @Valid AddMembersRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.addMembersToGroup(conversationId, request.memberIds())));
    }

    @DeleteMapping("/{conversationId}/members/{targetUserId}")
    @Operation(summary = "Kick a member from group conversation (Owner/Admin with role constraints)")
    public ResponseEntity<ApiResponse<ConversationResponse>> removeMemberFromGroup(
            @PathVariable String conversationId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.removeMemberFromGroup(conversationId, targetUserId)));
    }

    @PatchMapping("/{conversationId}/members/{targetUserId}/promote")
    @Operation(summary = "Promote a member to Admin (Owner only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> promoteToAdmin(
            @PathVariable String conversationId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.promoteToAdmin(conversationId, targetUserId)));
    }

    @PatchMapping("/{conversationId}/members/{targetUserId}/demote")
    @Operation(summary = "Demote an Admin back to Member (Owner only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> demoteFromAdmin(
            @PathVariable String conversationId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.demoteFromAdmin(conversationId, targetUserId)));
    }

    @GetMapping("/{conversationId}/group-members")
    @Operation(summary = "Get group members with pagination, search and friend-priority sorting")
    public ResponseEntity<ApiResponse<PageResponse<List<GroupMemberListItemResponse>>>> getGroupMembers(
            @PathVariable String conversationId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                                groupConversationService.getGroupMembers(conversationId, query, page, size)));
    }
}