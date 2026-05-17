package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.messageservice.dto.request.AddMembersRequest;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.request.JoinByLinkRequest;
import com.bondhub.messageservice.dto.request.LeaveGroupRequest;
import com.bondhub.messageservice.dto.request.MarkAsReadRequest;
import com.bondhub.messageservice.dto.request.UpdateGroupSettingsRequest;
import com.bondhub.messageservice.dto.request.UpdateJoinQuestionRequest;
import com.bondhub.messageservice.dto.response.*;
import com.bondhub.messageservice.model.PinnedMessageInfo;
import com.bondhub.messageservice.dto.request.GroupInviteSendRequest;
import com.bondhub.messageservice.service.conversation.ConversationService;
import com.bondhub.messageservice.service.conversation.GroupConversationService;
import com.bondhub.messageservice.service.conversation.GroupInviteService;
import com.bondhub.messageservice.service.message.PinService;
import com.bondhub.messageservice.service.conversation.JoinRequestService;
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
    private final GroupInviteService groupInviteService;
    private final PinService pinService;
    private final JoinRequestService joinRequestService;

    @GetMapping
    @Operation(summary = "Get conversations of current user (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationResponse>>>> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getUserConversations(page, size)));
    }

    @GetMapping("/quick")
    @Operation(summary = "Get 3 most recent conversations for quick access")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getQuickConversations(
            @RequestParam(defaultValue = "3") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getQuickConversations(size)));
    }

    @GetMapping("/groups/mine")
    @Operation(summary = "Get my group conversations with search, sort and filter")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationResponse>>>> getMyGroupConversations(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "activity_newest") String sort,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getMyGroupConversations(query, sort, filter, page, size)));
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
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String conversationId,
            @RequestBody(required = false) MarkAsReadRequest request) {
        String lastReadMessageId = request != null ? request.lastReadMessageId() : null;
        conversationService.markAsRead(conversationId, lastReadMessageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{conversationId}/unread-anchor")
    @Operation(summary = "Get first unread message ID and unread count")
    public ResponseEntity<ApiResponse<UnreadAnchorResponse>> getUnreadAnchor(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getUnreadAnchor(conversationId)));
    }

    @PostMapping("/groups")
    @Operation(summary = "Create a conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> createGroupConversation(
            @RequestBody @Valid GroupConversationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.createGroupConversation(request)));
    }

    @PostMapping("/groups/{conversationId}/invites")
    @Operation(summary = "Send group invites to non-friend users")
    public ResponseEntity<ApiResponse<Void>> sendGroupInvites(
            @PathVariable String conversationId,
            @RequestBody @Valid GroupInviteSendRequest request) {
        groupInviteService.sendInvites(conversationId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
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
            @RequestBody(required = false) LeaveGroupRequest request) {
        LeaveGroupRequest req = request != null ? request : new LeaveGroupRequest(false, null, false);
        groupConversationService.leaveGroup(conversationId, req);
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
            @PathVariable String targetUserId,
            @RequestParam(defaultValue = "false") boolean blockFromGroup) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.removeMemberFromGroup(conversationId, targetUserId, blockFromGroup)));
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

    @GetMapping("/{conversationId}/participants")
    @Operation(summary = "Get all participants in a conversation for filtering (supports Group and 1:1)")
    public ResponseEntity<ApiResponse<PageResponse<List<ConversationParticipantResponse>>>> getConversationParticipants(
            @PathVariable String conversationId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getConversationParticipants(conversationId, query, page, size)));
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

    @GetMapping("/{conversationId}/group-admins")
    @Operation(summary = "Get group owner and admins (owner first, admins sorted by name ASC)")
    public ResponseEntity<ApiResponse<PageResponse<List<AdminMemberResponse>>>> getGroupAdmins(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getGroupAdmins(conversationId, page, size)));
    }

    @GetMapping("/{conversationId}/admin-candidates")
    @Operation(summary = "Get non-owner members for admin management (admins first then members, sorted by name ASC). Owner only.")
    public ResponseEntity<ApiResponse<PageResponse<List<AdminMemberResponse>>>> getAdminCandidates(
            @PathVariable String conversationId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getAdminCandidates(conversationId, query, page, size)));
    }

    @PatchMapping("/{conversationId}/transfer-owner/{targetUserId}")
    @Operation(summary = "Transfer group ownership to another member (Owner only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> transferOwnership(
            @PathVariable String conversationId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.transferOwnership(conversationId, targetUserId)));
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
                joinRequestService.getJoinPreview(token)));
    }

    @PostMapping("/join/{token}")
    @Operation(summary = "Join a group conversation using an invite link token (creates pending request if approval is enabled)")
    public ResponseEntity<ApiResponse<ConversationResponse>> joinByLink(
            @PathVariable String token,
            @RequestBody(required = false) JoinByLinkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                joinRequestService.joinByLink(token, request)));
    }

    @PutMapping("/{conversationId}/join-question")
    @Operation(summary = "Set/update the join question for a group (Owner/Admin only, requires membershipApprovalEnabled)")
    public ResponseEntity<ApiResponse<Void>> updateJoinQuestion(
            @PathVariable String conversationId,
            @RequestBody @Valid UpdateJoinQuestionRequest request) {
        joinRequestService.updateJoinQuestion(conversationId, request.question());
        return ResponseEntity.ok(ApiResponse.success(null));
    }


    @GetMapping("/{conversationId}/join-requests")
    @Operation(summary = "Get pending join requests for a group (Owner/Admin only)")
    public ResponseEntity<ApiResponse<PageResponse<List<JoinRequestResponse>>>> getJoinRequests(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                joinRequestService.getJoinRequests(conversationId, page, size)));
    }

    @PostMapping("/{conversationId}/join-requests/{requestId}/approve")
    @Operation(summary = "Approve a pending join request (Owner/Admin only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> approveJoinRequest(
            @PathVariable String conversationId,
            @PathVariable String requestId) {
        return ResponseEntity.ok(ApiResponse.success(
                joinRequestService.approveJoinRequest(conversationId, requestId)));
    }

    @PostMapping("/{conversationId}/join-requests/{requestId}/reject")
    @Operation(summary = "Reject a pending join request (Owner/Admin only)")
    public ResponseEntity<ApiResponse<Void>> rejectJoinRequest(
            @PathVariable String conversationId,
            @PathVariable String requestId) {
        joinRequestService.rejectJoinRequest(conversationId, requestId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{conversationId}/join-requests/me")
    @Operation(summary = "Cancel my pending join request")
    public ResponseEntity<ApiResponse<Void>> cancelMyJoinRequest(@PathVariable String conversationId) {
        joinRequestService.cancelMyJoinRequest(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{conversationId}/block/{targetUserId}")
    @Operation(summary = "Block a member from group — removes them and prevents rejoin via link or being added (Owner/Admin with role constraints)")
    public ResponseEntity<ApiResponse<ConversationResponse>> blockMemberFromGroup(
            @PathVariable String conversationId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.blockMemberFromGroup(conversationId, targetUserId)));
    }

    @DeleteMapping("/{conversationId}/block/{targetUserId}")
    @Operation(summary = "Unblock a member from group (Owner only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> unblockMemberFromGroup(
            @PathVariable String conversationId,
            @PathVariable String targetUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.unblockMemberFromGroup(conversationId, targetUserId)));
    }

    @GetMapping("/{conversationId}/blocked-members")
    @Operation(summary = "Get blocked members list for a group (Owner/Admin only)")
    public ResponseEntity<ApiResponse<PageResponse<List<SearchMemberResponse>>>> getBlockedMembers(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getBlockedMembers(conversationId, page, size)));
    }

    @GetMapping("/{conversationId}/block-candidates")
    @Operation(summary = "Get active members with MEMBER role eligible for blocking (Owner/Admin only)")
    public ResponseEntity<ApiResponse<PageResponse<List<SearchMemberResponse>>>> getBlockCandidates(
            @PathVariable String conversationId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupConversationService.getBlockCandidates(conversationId, query, page, size)));
    }

    @GetMapping("/{conversationId}/pins")
    @Operation(summary = "Get pinned messages for a conversation")
    public ResponseEntity<ApiResponse<List<PinnedMessageInfo>>> getPins(@PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(pinService.getPins(conversationId)));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/pin")
    @Operation(summary = "Pin a message in a conversation (max 3)")
    public ResponseEntity<ApiResponse<PinnedMessageInfo>> pinMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        return ResponseEntity.ok(ApiResponse.success(pinService.pinMessage(conversationId, messageId)));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}/pin")
    @Operation(summary = "Unpin a message from a conversation")
    public ResponseEntity<ApiResponse<Void>> unpinMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        pinService.unpinMessage(conversationId, messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}