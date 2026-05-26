package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.request.LeaveGroupRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.service.message.SystemMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatUserRepository chatUserRepository;
    @Mock
    private SystemMessageService systemMessageService;
    @Mock
    private ConversationHelper helper;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private OutboxEventPublisher eventPublisher;

    @InjectMocks
    private GroupConversationServiceImpl groupConversationService;

    private String currentUserId;
    private SecurityUtil securityUtil;

    @BeforeEach
    void setUp() {
        currentUserId = "user1";
        securityUtil = mock(SecurityUtil.class);
        org.mockito.Mockito.lenient().when(helper.isActiveMember(any(ConversationMember.class)))
                .thenAnswer(i -> {
                    ConversationMember m = i.getArgument(0);
                    return m != null && m.getActive() != null ? m.getActive() : false;
                });
    }

    // ==========================================
    // UT Lab 3: createGroupConversation Tests
    // ==========================================

    @Test
    @DisplayName("UTCID01 - createGroupConversation: Thành công khi số lượng member >= 2 và đều là bạn bè")
    void createGroup_Success() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        GroupConversationCreateRequest request = new GroupConversationCreateRequest("Nhóm 1", "avatar.png", List.of("user2", "user3"));
        
        ChatUser user2 = new ChatUser(); user2.setId("user2"); user2.setFullName("User 2");
        ChatUser user3 = new ChatUser(); user3.setId("user3"); user3.setFullName("User 3");
        when(chatUserRepository.findAllById(anySet())).thenReturn(List.of(user2, user3));
        
        ChatUser currentUser = new ChatUser();
        currentUser.setId(currentUserId);
        currentUser.setFriendIds(Set.of("user2", "user3"));
        when(chatUserRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        
        // Mocking for existing group check
        when(mongoTemplate.find(any(Query.class), eq(Conversation.class))).thenReturn(Collections.emptyList());

        Conversation savedConversation = Conversation.builder().id("group1").members(new HashSet<>()).build();
        when(conversationRepository.save(any(Conversation.class))).thenReturn(savedConversation);

        ConversationHelper.ActorInfo actorInfo = new ConversationHelper.ActorInfo("Actor", "actor_avt");
        when(helper.fetchActorInfo(currentUserId)).thenReturn(actorInfo);

        ConversationResponse responseMock = ConversationResponse.builder().build();
        when(helper.broadcastAndRespond(savedConversation, currentUserId)).thenReturn(responseMock);

        ConversationResponse result = groupConversationService.createGroupConversation(request);

        assertNotNull(result);
        verify(conversationRepository).save(any(Conversation.class));
        verify(systemMessageService).sendSystemMessage(eq("group1"), eq(currentUserId), anyString(), anyString(), eq(SystemActionType.CREATE_GROUP), anyMap());
    }

    @Test
    @DisplayName("UTCID02 - createGroupConversation: Thất bại do số lượng member truyền vào < 2")
    void createGroup_Fail_InvalidMemberCount() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        GroupConversationCreateRequest request = new GroupConversationCreateRequest("Nhóm 1", null, List.of("user2")); // only 1 member + current = 2 total, but memberIds size < 2

        AppException ex = assertThrows(AppException.class, () -> groupConversationService.createGroupConversation(request));
        assertEquals(ErrorCode.CHAT_INVALID_MEMBER_COUNT, ex.getErrorCode());
    }

    @Test
    @DisplayName("UTCID03 - createGroupConversation: Thất bại do không tìm thấy đủ người dùng")
    void createGroup_Fail_UserNotFound() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        GroupConversationCreateRequest request = new GroupConversationCreateRequest("Nhóm 1", null, List.of("user2", "user3"));
        
        ChatUser user2 = new ChatUser(); user2.setId("user2");
        when(chatUserRepository.findAllById(anySet())).thenReturn(List.of(user2)); // user3 not found

        AppException ex = assertThrows(AppException.class, () -> groupConversationService.createGroupConversation(request));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("UTCID04 - createGroupConversation: Thất bại do không có ít nhất 1 bạn bè trong danh sách")
    void createGroup_Fail_NoFriends() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        GroupConversationCreateRequest request = new GroupConversationCreateRequest("Nhóm 1", null, List.of("user2", "user3"));
        
        ChatUser user2 = new ChatUser(); user2.setId("user2");
        ChatUser user3 = new ChatUser(); user3.setId("user3");
        when(chatUserRepository.findAllById(anySet())).thenReturn(List.of(user2, user3));
        
        ChatUser currentUser = new ChatUser();
        currentUser.setId(currentUserId);
        currentUser.setFriendIds(Set.of("user4")); // No friends match user2, user3
        when(chatUserRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        
        when(mongoTemplate.find(any(Query.class), eq(Conversation.class))).thenReturn(Collections.emptyList());

        AppException ex = assertThrows(AppException.class, () -> groupConversationService.createGroupConversation(request));
        assertEquals(ErrorCode.CHAT_NEED_AT_LEAST_ONE_FRIEND, ex.getErrorCode());
    }

    @Test
    @DisplayName("UTCID05 - createGroupConversation: Trả về group hiện tại do phát hiện trùng group")
    void createGroup_Success_ReturnExistingDuplicateGroup() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        GroupConversationCreateRequest request = new GroupConversationCreateRequest("Nhóm 1", null, List.of("user2", "user3"));
        
        ChatUser user2 = new ChatUser(); user2.setId("user2");
        ChatUser user3 = new ChatUser(); user3.setId("user3");
        when(chatUserRepository.findAllById(anySet())).thenReturn(List.of(user2, user3));
        
        Conversation existingGroup = Conversation.builder().id("existing_group").name("Nhóm 1").members(Set.of(
                ConversationMember.builder().userId(currentUserId).active(true).build(),
                ConversationMember.builder().userId("user2").active(true).build(),
                ConversationMember.builder().userId("user3").active(true).build()
        )).build();
        when(mongoTemplate.find(any(Query.class), eq(Conversation.class))).thenReturn(List.of(existingGroup));
        
        ConversationResponse mockResponse = ConversationResponse.builder().build();
        when(helper.buildConversationResponseForCurrentUser(existingGroup, currentUserId)).thenReturn(mockResponse);

        ConversationResponse result = groupConversationService.createGroupConversation(request);

        assertNotNull(result);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID06 - createGroupConversation: Thành công tạo group có chứa người chưa kết bạn (gửi link mời)")
    void createGroup_Success_WithNonFriends() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        GroupConversationCreateRequest request = new GroupConversationCreateRequest("Nhóm 1", null, List.of("user2", "user3"));
        
        ChatUser user2 = new ChatUser(); user2.setId("user2"); user2.setFullName("User 2");
        ChatUser user3 = new ChatUser(); user3.setId("user3"); user3.setFullName("User 3"); // Non-friend
        when(chatUserRepository.findAllById(anySet())).thenReturn(List.of(user2, user3));
        
        ChatUser currentUser = new ChatUser();
        currentUser.setId(currentUserId);
        currentUser.setFriendIds(Set.of("user2")); // only user2 is friend
        when(chatUserRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        
        when(mongoTemplate.find(any(Query.class), eq(Conversation.class))).thenReturn(Collections.emptyList());

        Conversation savedConversation = Conversation.builder().id("group1").members(new HashSet<>()).build();
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> {
            Conversation c = i.getArgument(0);
            assertTrue(c.getSettings().isJoinByLinkEnabled());
            assertNotNull(c.getJoinLinkToken());
            assertTrue(c.getInvitedUserIds().contains("user3"));
            return savedConversation;
        });

        ConversationHelper.ActorInfo actorInfo = new ConversationHelper.ActorInfo("Actor", "actor_avt");
        when(helper.fetchActorInfo(currentUserId)).thenReturn(actorInfo);
        when(helper.broadcastAndRespond(savedConversation, currentUserId)).thenReturn(ConversationResponse.builder().build());

        groupConversationService.createGroupConversation(request);

        verify(conversationRepository).save(any(Conversation.class));
    }


    // ==========================================
    // UT Lab 4: leaveGroup Tests
    // ==========================================

    @Test
    @DisplayName("UTCID01 - leaveGroup: Thành viên rời nhóm thành công (không im lặng)")
    void leaveGroup_Success_RegularMember() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        String conversationId = "group1";
        LeaveGroupRequest request = new LeaveGroupRequest(false, null, false);
        Conversation conversation = Conversation.builder().id(conversationId).unreadCounts(new HashMap<>()).build();
        
        ConversationMember currentMember = ConversationMember.builder().userId(currentUserId).role(MemberRole.MEMBER).active(true).build();
        ConversationMember otherMember = ConversationMember.builder().userId("user2").role(MemberRole.OWNER).active(true).build();
        conversation.setMembers(new HashSet<>(List.of(currentMember, otherMember)));

        when(helper.findGroupConversation(conversationId)).thenReturn(conversation);
        when(helper.getMemberOrThrow(conversation, currentUserId)).thenReturn(currentMember);
        when(helper.resolveRole(currentMember)).thenReturn(MemberRole.MEMBER);
        when(helper.fetchActorInfo(currentUserId)).thenReturn(new ConversationHelper.ActorInfo("Actor", "Actor"));

        groupConversationService.leaveGroup(conversationId, request);

        assertFalse(currentMember.getActive());
        assertNotNull(currentMember.getRemovedAt());
        verify(conversationRepository).save(conversation);
        verify(systemMessageService).sendSystemMessage(eq(conversationId), eq(currentUserId), anyString(), anyString(), eq(SystemActionType.LEAVE_GROUP), anyMap(), eq(Set.of("user2")));
        verify(helper).broadcastConversationUpdate(conversation);
    }

    @Test
    @DisplayName("UTCID02 - leaveGroup: Chủ phòng rời nhóm và nhượng quyền thành công")
    void leaveGroup_Success_OwnerTransfer() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        String conversationId = "group1";
        LeaveGroupRequest request = new LeaveGroupRequest(false, "user2", false);
        Conversation conversation = Conversation.builder().id(conversationId).build();
        
        ConversationMember currentMember = ConversationMember.builder().userId(currentUserId).role(MemberRole.OWNER).active(true).build();
        ConversationMember targetMember = ConversationMember.builder().userId("user2").role(MemberRole.MEMBER).active(true).build();
        
        conversation.setMembers(new HashSet<>(List.of(currentMember, targetMember)));

        when(helper.findGroupConversation(conversationId)).thenReturn(conversation);
        when(helper.getMemberOrThrow(conversation, currentUserId)).thenReturn(currentMember);
        when(helper.getMemberOrThrow(conversation, "user2")).thenReturn(targetMember);
        when(helper.resolveRole(currentMember)).thenReturn(MemberRole.OWNER);
        
        when(helper.fetchActorInfo(currentUserId)).thenReturn(new ConversationHelper.ActorInfo("Actor", "Actor"));
        when(helper.fetchActorInfo("user2")).thenReturn(new ConversationHelper.ActorInfo("User2", "User2"));

        groupConversationService.leaveGroup(conversationId, request);

        assertEquals(MemberRole.MEMBER, currentMember.getRole());
        assertEquals(MemberRole.OWNER, targetMember.getRole());
        assertFalse(currentMember.getActive());
        verify(conversationRepository).save(conversation);
        verify(systemMessageService).sendSystemMessage(eq(conversationId), eq(currentUserId), anyString(), anyString(), eq(SystemActionType.TRANSFER_OWNER), anyMap());
    }

    @Test
    @DisplayName("UTCID03 - leaveGroup: Thất bại do chủ phòng rời nhóm nhưng không nhượng quyền")
    void leaveGroup_Fail_OwnerLeaveWithoutTransfer() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        String conversationId = "group1";
        LeaveGroupRequest request = new LeaveGroupRequest(false, null, false);
        Conversation conversation = Conversation.builder().id(conversationId).build();
        
        ConversationMember currentMember = ConversationMember.builder().userId(currentUserId).role(MemberRole.OWNER).active(true).build();

        when(helper.findGroupConversation(conversationId)).thenReturn(conversation);
        when(helper.getMemberOrThrow(conversation, currentUserId)).thenReturn(currentMember);
        when(helper.resolveRole(currentMember)).thenReturn(MemberRole.OWNER);

        AppException ex = assertThrows(AppException.class, () -> groupConversationService.leaveGroup(conversationId, request));
        assertEquals(ErrorCode.CHAT_CANNOT_REMOVE_OWNER, ex.getErrorCode());
    }

    @Test
    @DisplayName("UTCID04 - leaveGroup: Thất bại do người được nhượng quyền không phải thành viên")
    void leaveGroup_Fail_TargetNotMember() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        String conversationId = "group1";
        LeaveGroupRequest request = new LeaveGroupRequest(false, "user2", false);
        Conversation conversation = Conversation.builder().id(conversationId).build();
        
        ConversationMember currentMember = ConversationMember.builder().userId(currentUserId).role(MemberRole.OWNER).active(true).build();
        ConversationMember targetMember = ConversationMember.builder().userId("user2").role(MemberRole.MEMBER).active(false).build(); // Not active
        
        when(helper.findGroupConversation(conversationId)).thenReturn(conversation);
        when(helper.getMemberOrThrow(conversation, currentUserId)).thenReturn(currentMember);
        when(helper.getMemberOrThrow(conversation, "user2")).thenReturn(targetMember);
        when(helper.resolveRole(currentMember)).thenReturn(MemberRole.OWNER);

        AppException ex = assertThrows(AppException.class, () -> groupConversationService.leaveGroup(conversationId, request));
        assertEquals(ErrorCode.CHAT_TARGET_NOT_MEMBER, ex.getErrorCode());
    }

    @Test
    @DisplayName("UTCID05 - leaveGroup: Thành công rời nhóm và blockReJoin")
    void leaveGroup_Success_BlockReJoin() {
        when(helper.getSecurityUtil()).thenReturn(securityUtil);
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);

        String conversationId = "group1";
        LeaveGroupRequest request = new LeaveGroupRequest(false, null, true);
        Conversation conversation = Conversation.builder().id(conversationId).build();
        
        ConversationMember currentMember = ConversationMember.builder().userId(currentUserId).role(MemberRole.MEMBER).active(true).build();
        conversation.setMembers(new HashSet<>(List.of(currentMember)));

        when(helper.findGroupConversation(conversationId)).thenReturn(conversation);
        when(helper.getMemberOrThrow(conversation, currentUserId)).thenReturn(currentMember);
        when(helper.resolveRole(currentMember)).thenReturn(MemberRole.MEMBER);
        when(helper.fetchActorInfo(currentUserId)).thenReturn(new ConversationHelper.ActorInfo("Actor", "Actor"));

        groupConversationService.leaveGroup(conversationId, request);

        assertTrue(conversation.getSelfBlockedUserIds().contains(currentUserId));
        verify(conversationRepository).save(conversation);
    }
}
