package com.bondhub.messageservice.service.conversation;

import com.bondhub.messageservice.dto.request.UpdateGroupSettingsRequest;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.GroupMemberListItemResponse;
import com.bondhub.messageservice.dto.response.JoinGroupPreviewResponse;
import com.bondhub.messageservice.dto.response.SearchMemberResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface GroupConversationService {

    ConversationResponse createGroupConversation(GroupConversationCreateRequest request);

    ConversationResponse addMembersToGroup(String conversationId, List<String> memberIds);

    ConversationResponse removeMemberFromGroup(String conversationId, String targetUserId);

    ConversationResponse promoteToAdmin(String conversationId, String targetUserId);

    ConversationResponse demoteFromAdmin(String conversationId, String targetUserId);

    ConversationResponse updateGroupName(String conversationId, String name);

    ConversationResponse updateGroupAvatar(String conversationId, MultipartFile file);

    ConversationResponse updateGroupSettings(String conversationId, UpdateGroupSettingsRequest request);

    void disbandGroup(String conversationId);

    void leaveGroup(String conversationId, boolean silent, String transferTo);

    PageResponse<List<SearchMemberResponse>> searchMembersToAdd(String conversationId, String query, int page, int size);

    Map<String, List<SearchMemberResponse>> getFriendsDirectory(String conversationId);

    PageResponse<List<GroupMemberListItemResponse>> getGroupMembers(String conversationId, String query, int page, int size);

    String generateJoinLink(String conversationId);

    String refreshJoinLink(String conversationId);

    ConversationResponse joinByLink(String token);

    JoinGroupPreviewResponse getJoinPreview(String token);
}
