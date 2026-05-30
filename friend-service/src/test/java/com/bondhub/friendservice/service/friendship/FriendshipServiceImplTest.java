package com.bondhub.friendservice.service.friendship;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.friendservice.client.SocketServiceClient;
import com.bondhub.friendservice.client.UserServiceClient;
import com.bondhub.friendservice.dto.request.FriendRequestSendRequest;
import com.bondhub.friendservice.dto.response.FriendRequestResponse;
import com.bondhub.friendservice.graph.service.GraphFriendService;
import com.bondhub.friendservice.mapper.FriendShipMapper;
import com.bondhub.friendservice.model.FriendShip;
import com.bondhub.friendservice.model.enums.FriendStatus;
import com.bondhub.friendservice.repository.FriendShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceImplTest {

    @Mock
    private FriendShipRepository friendShipRepository;
    @Mock
    private FriendShipMapper friendShipMapper;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private RawNotificationEventPublisher rawNotificationEventPublisher;
    @Mock
    private GraphFriendService graphFriendService;
    @Mock
    private SocketServiceClient socketServiceClient;

    @InjectMocks
    private FriendshipServiceImpl friendshipService;

    private String currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = "user1";
        when(securityUtil.getCurrentUserId()).thenReturn(currentUserId);
    }

    @Test
    @DisplayName("UC33-UTCID01 - Send friend request success")
    void sendFriendRequest_Success() {
        FriendRequestSendRequest request = new FriendRequestSendRequest("user2", "hi");
        FriendShip saved = buildPendingRequest("fr1", currentUserId, "user2");

        when(userServiceClient.getUserSummary("user2"))
                .thenReturn(ApiResponse.success(buildUser("user2")))
                .thenReturn(ApiResponse.success(buildUser("user2")));
        when(userServiceClient.getUserSummary(currentUserId))
                .thenReturn(ApiResponse.success(buildUser(currentUserId)));
        when(friendShipRepository.findFriendshipBetweenUsers(currentUserId, "user2"))
                .thenReturn(Optional.empty());
        when(friendShipRepository.save(any(FriendShip.class))).thenReturn(saved);
        when(friendShipMapper.toFriendRequestResponse(any(), any(), any()))
                .thenReturn(FriendRequestResponse.builder().id("fr1").build());

        FriendRequestResponse response = friendshipService.sendFriendRequest(request);

        assertEquals("fr1", response.id());
        verify(outboxEventPublisher).saveAndPublish(eq(currentUserId), any(), any(), any());
        verify(rawNotificationEventPublisher).publish(any());
    }

    @Test
    @DisplayName("UC33-UTCID02 - Send friend request receiver not found")
    void sendFriendRequest_ReceiverNotFound() {
        FriendRequestSendRequest request = new FriendRequestSendRequest("user2", "hi");
        when(userServiceClient.getUserSummary("user2"))
                .thenReturn(ApiResponse.success(null));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(request));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        verify(friendShipRepository, never()).save(any());
    }

    @Test
    @DisplayName("UC33-UTCID03 - Send friend request to self")
    void sendFriendRequest_ToSelf() {
        FriendRequestSendRequest request = new FriendRequestSendRequest(currentUserId, "hi");
        when(userServiceClient.getUserSummary(currentUserId))
                .thenReturn(ApiResponse.success(buildUser(currentUserId)));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(request));
        assertEquals(ErrorCode.CANNOT_FRIEND_YOURSELF, ex.getErrorCode());
        verify(friendShipRepository, never()).save(any());
    }

    @Test
    @DisplayName("UC33-UTCID04 - Send friend request already friends")
    void sendFriendRequest_AlreadyFriends() {
        FriendRequestSendRequest request = new FriendRequestSendRequest("user2", "hi");
        FriendShip existing = FriendShip.builder()
                .id("fr2")
                .requested(currentUserId)
                .received("user2")
                .friendStatus(FriendStatus.ACCEPTED)
                .build();

        when(userServiceClient.getUserSummary("user2"))
                .thenReturn(ApiResponse.success(buildUser("user2")));
        when(friendShipRepository.findFriendshipBetweenUsers(currentUserId, "user2"))
                .thenReturn(Optional.of(existing));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(request));
        assertEquals(ErrorCode.ALREADY_FRIENDS, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC33-UTCID05 - Send friend request already pending")
    void sendFriendRequest_AlreadyPending() {
        FriendRequestSendRequest request = new FriendRequestSendRequest("user2", "hi");
        FriendShip existing = buildPendingRequest("fr3", currentUserId, "user2");

        when(userServiceClient.getUserSummary("user2"))
                .thenReturn(ApiResponse.success(buildUser("user2")));
        when(friendShipRepository.findFriendshipBetweenUsers(currentUserId, "user2"))
                .thenReturn(Optional.of(existing));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(request));
        assertEquals(ErrorCode.FRIEND_REQUEST_ALREADY_SENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC33-UTCID06 - Send friend request with user-service error")
    void sendFriendRequest_UserServiceError() {
        FriendRequestSendRequest request = new FriendRequestSendRequest("user2", "hi");
        when(userServiceClient.getUserSummary("user2"))
                .thenThrow(new RuntimeException("user-service down"));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(request));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

        @Test
        @DisplayName("UC33-UTCID07 - Send friend request with empty receiverId")
        void sendFriendRequest_EmptyReceiverId() {
                FriendRequestSendRequest request = new FriendRequestSendRequest("", "hi");
                when(userServiceClient.getUserSummary(""))
                                .thenReturn(ApiResponse.success(null));

                AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(request));
                assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
                verify(friendShipRepository, never()).save(any());
        }

    @Test
    @DisplayName("UC35-UTCID01 - Accept friend request success")
    void acceptFriendRequest_Success() {
        FriendShip friendShip = buildPendingRequest("fr6", "user2", currentUserId);

        when(friendShipRepository.findById("fr6")).thenReturn(Optional.of(friendShip));
        when(friendShipRepository.save(any(FriendShip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userServiceClient.getUserSummary("user2")).thenReturn(ApiResponse.success(buildUser("user2")));
        when(userServiceClient.getUserSummary(currentUserId))
                .thenReturn(ApiResponse.success(buildUser(currentUserId)));
        when(friendShipMapper.toFriendRequestResponse(any(), any(), any()))
                .thenReturn(FriendRequestResponse.builder().id("fr6").build());

        FriendRequestResponse response = friendshipService.acceptFriendRequest("fr6");

        assertEquals("fr6", response.id());
        verify(graphFriendService).createFriendRelationship("user2", currentUserId);
        verify(outboxEventPublisher).saveAndPublish(eq("user2"), any(), any(), any());
        verify(rawNotificationEventPublisher).publish(any());
        verify(rawNotificationEventPublisher).publishCleanup(any());

        ArgumentCaptor<FriendShip> captor = ArgumentCaptor.forClass(FriendShip.class);
        verify(friendShipRepository).save(captor.capture());
        assertEquals(FriendStatus.ACCEPTED, captor.getValue().getFriendStatus());
    }

    @Test
    @DisplayName("UC35-UTCID02 - Accept friend request not found")
    void acceptFriendRequest_NotFound() {
        when(friendShipRepository.findById("missing")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> friendshipService.acceptFriendRequest("missing"));
        assertEquals(ErrorCode.FRIEND_REQUEST_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC35-UTCID03 - Accept friend request not authorized")
    void acceptFriendRequest_NotAuthorized() {
        FriendShip friendShip = buildPendingRequest("fr7", "user2", "otherUser");
        when(friendShipRepository.findById("fr7")).thenReturn(Optional.of(friendShip));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.acceptFriendRequest("fr7"));
        assertEquals(ErrorCode.NOT_AUTHORIZED_TO_ACCEPT, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC35-UTCID04 - Accept friend request not pending")
    void acceptFriendRequest_NotPending() {
        FriendShip friendShip = FriendShip.builder()
                .id("fr8")
                .requested("user2")
                .received(currentUserId)
                .friendStatus(FriendStatus.ACCEPTED)
                .build();
        when(friendShipRepository.findById("fr8")).thenReturn(Optional.of(friendShip));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.acceptFriendRequest("fr8"));
        assertEquals(ErrorCode.FRIEND_REQUEST_NOT_PENDING, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC35-UTCID05 - Accept friend request with graph error")
    void acceptFriendRequest_GraphError() {
        FriendShip friendShip = buildPendingRequest("fr9", "user2", currentUserId);

        when(friendShipRepository.findById("fr9")).thenReturn(Optional.of(friendShip));
        when(friendShipRepository.save(any(FriendShip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userServiceClient.getUserSummary("user2")).thenReturn(ApiResponse.success(buildUser("user2")));
        when(userServiceClient.getUserSummary(currentUserId))
                .thenReturn(ApiResponse.success(buildUser(currentUserId)));
        when(friendShipMapper.toFriendRequestResponse(any(), any(), any()))
                .thenReturn(FriendRequestResponse.builder().id("fr9").build());
        doThrow(new RuntimeException("neo4j down"))
                .when(graphFriendService).createFriendRelationship("user2", currentUserId);

        assertDoesNotThrow(() -> friendshipService.acceptFriendRequest("fr9"));
        verify(outboxEventPublisher).saveAndPublish(eq("user2"), any(), any(), any());
        verify(rawNotificationEventPublisher).publish(any());
        verify(rawNotificationEventPublisher).publishCleanup(any());
    }

    @Test
    @DisplayName("UC35-UTCID06 - Accept friend request saves accepted status")
    void acceptFriendRequest_SavesAcceptedStatus() {
        FriendShip friendShip = buildPendingRequest("fr10", "user2", currentUserId);

        when(friendShipRepository.findById("fr10")).thenReturn(Optional.of(friendShip));
        when(friendShipRepository.save(any(FriendShip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userServiceClient.getUserSummary("user2")).thenReturn(ApiResponse.success(buildUser("user2")));
        when(userServiceClient.getUserSummary(currentUserId))
                .thenReturn(ApiResponse.success(buildUser(currentUserId)));
        when(friendShipMapper.toFriendRequestResponse(any(), any(), any()))
                .thenReturn(FriendRequestResponse.builder().id("fr10").build());

        friendshipService.acceptFriendRequest("fr10");

        ArgumentCaptor<FriendShip> captor = ArgumentCaptor.forClass(FriendShip.class);
        verify(friendShipRepository).save(captor.capture());
        assertEquals(FriendStatus.ACCEPTED, captor.getValue().getFriendStatus());
    }

        @Test
        @DisplayName("UC35-UTCID07 - Accept friend request with empty id")
        void acceptFriendRequest_EmptyId() {
                when(friendShipRepository.findById("")).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class, () -> friendshipService.acceptFriendRequest(""));
                assertEquals(ErrorCode.FRIEND_REQUEST_NOT_FOUND, ex.getErrorCode());
        }

    private FriendShip buildPendingRequest(String id, String requested, String received) {
        return FriendShip.builder()
                .id(id)
                .requested(requested)
                .received(received)
                .friendStatus(FriendStatus.PENDING)
                .build();
    }

    private UserSummaryResponse buildUser(String id) {
        return UserSummaryResponse.builder()
                .id(id)
                .fullName("User " + id)
                .avatar("avatar")
                .build();
    }
}
