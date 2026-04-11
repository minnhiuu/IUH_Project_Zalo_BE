package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.request.AddMembersRequest;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.request.UpdateGroupSettingsRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.GroupMemberListItemResponse;
import com.bondhub.messageservice.dto.response.JoinGroupPreviewResponse;
import com.bondhub.messageservice.dto.response.JoinRequestResponse;
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

    @PatchMapping("/{conversationId}/settings")
    @Operation(summary = "Update group settings (Owner/Admin only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateGroupSettings(
            @PathVariable String conversationId,
            @RequestBody UpdateGroupSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.updateGroupSettings(conversationId, request)));
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
    @Operation(summary = "Leave group conversation (Owner can pass transferTo to transfer ownership before leaving)")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "false") boolean silent,
            @RequestParam(required = false) String transferTo) {
                groupConversationService.leaveGroup(conversationId, silent, transferTo);
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

    @PostMapping("/{conversationId}/join-link")
    @Operation(summary = "Generate group join link for the first time (Owner/Admin only)")
    public ResponseEntity<ApiResponse<String>> generateJoinLink(@PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.generateJoinLink(conversationId)));
    }

    @PostMapping("/{conversationId}/join-link/refresh")
    @Operation(summary = "Refresh group join link — invalidates old link (Owner/Admin only)")
    public ResponseEntity<ApiResponse<String>> refreshJoinLink(@PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.refreshJoinLink(conversationId)));
    }

    @GetMapping("/join/{token}/preview")
    @Operation(summary = "Get group preview info before joining via invite link")
    public ResponseEntity<ApiResponse<JoinGroupPreviewResponse>> getJoinPreview(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getJoinPreview(token)));
    }

    @PostMapping("/join/{token}")
    @Operation(summary = "Join a group conversation using an invite link token (creates pending request if approval is enabled)")
    public ResponseEntity<ApiResponse<ConversationResponse>> joinByLink(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.joinByLink(token)));
    }

    @GetMapping("/{conversationId}/join-requests")
    @Operation(summary = "Get pending join requests for a group (Owner/Admin only)")
    public ResponseEntity<ApiResponse<PageResponse<List<JoinRequestResponse>>>> getJoinRequests(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getJoinRequests(conversationId, page, size)));
    }

    @PostMapping("/{conversationId}/join-requests/{requestId}/approve")
    @Operation(summary = "Approve a pending join request (Owner/Admin only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> approveJoinRequest(
            @PathVariable String conversationId,
            @PathVariable String requestId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.approveJoinRequest(conversationId, requestId)));
    }

    @PostMapping("/{conversationId}/join-requests/{requestId}/reject")
    @Operation(summary = "Reject a pending join request (Owner/Admin only)")
    public ResponseEntity<ApiResponse<Void>> rejectJoinRequest(
            @PathVariable String conversationId,
            @PathVariable String requestId) {
        groupConversationService.rejectJoinRequest(conversationId, requestId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{conversationId}/join-requests/me")
    @Operation(summary = "Cancel my pending join request")
    public ResponseEntity<ApiResponse<Void>> cancelMyJoinRequest(@PathVariable String conversationId) {
        groupConversationService.cancelMyJoinRequest(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}