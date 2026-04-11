package com.bondhub.friendservice.service.friendship;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.friendservice.client.UserServiceClient;
import com.bondhub.friendservice.dto.request.FriendRequestSendRequest;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.event.notification.CleanupNotificationEvent;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.friendservice.dto.response.*;
import com.bondhub.common.enums.FriendshipAction;
import com.bondhub.common.event.friend.FriendshipChangedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.friendservice.mapper.FriendShipMapper;
import com.bondhub.friendservice.model.FriendShip;
import com.bondhub.friendservice.model.enums.FriendStatus;
import com.bondhub.friendservice.repository.FriendShipRepository;
import com.bondhub.friendservice.graph.service.GraphFriendService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {
    FriendShipRepository friendShipRepository;
    FriendShipMapper friendShipMapper;
    UserServiceClient userServiceClient;
    SecurityUtil securityUtil;
    OutboxEventPublisher outboxEventPublisher;
    RawNotificationEventPublisher rawNotificationEventPublisher;
    GraphFriendService graphFriendService;

    @Override
    @Transactional
    public FriendRequestResponse sendFriendRequest(FriendRequestSendRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        String receiverId = request.receiverId();

        log.info("User {} sending friend request to {}", currentUserId, receiverId);

        validateUserExists(receiverId);

        if (currentUserId.equals(receiverId)) {
            throw new AppException(ErrorCode.CANNOT_FRIEND_YOURSELF);
        }

        Optional<FriendShip> existingFriendship = friendShipRepository
                .findFriendshipBetweenUsers(currentUserId, receiverId);

        if (existingFriendship.isPresent()) {
            FriendShip friendship = existingFriendship.get();
            if (friendship.getFriendStatus() == FriendStatus.ACCEPTED) {
                throw new AppException(ErrorCode.ALREADY_FRIENDS);
            } else if (friendship.getFriendStatus() == FriendStatus.PENDING) {
                throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
            }
        }

        FriendShip friendShip = FriendShip.builder()
                .requested(currentUserId)
                .received(receiverId)
                .content(request.message())
                .friendStatus(FriendStatus.PENDING)
                .build();

        friendShip = friendShipRepository.save(friendShip);
        publishFriendshipEvent(friendShip.getRequested(), friendShip.getReceived(), friendShip.getId(), FriendshipAction.REQUESTED);
        log.info("Friend request created with id: {}", friendShip.getId());

        UserSummaryResponse requester = getUserSummary(friendShip.getRequested());
        UserSummaryResponse receiver = getUserSummary(friendShip.getReceived());

        RawNotificationEvent notificationEvent = RawNotificationEvent.builder()
                .recipientId(receiverId)
                .actorId(currentUserId)
                .actorName(requester.fullName())
                .actorAvatar(requester.avatar())
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(friendShip.getId())
                .payload(Map.of("requestId", friendShip.getId()))
                .occurredAt(LocalDateTime.now())
                .build();
        rawNotificationEventPublisher.publish(notificationEvent);

        return friendShipMapper.toFriendRequestResponse(friendShip, requester, receiver);
    }

    @Override
    @Transactional
    public FriendRequestResponse acceptFriendRequest(String friendshipId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("User {} accepting friend request {}", currentUserId, friendshipId);

        FriendShip friendShip = friendShipRepository.findById(friendshipId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendShip.getReceived().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_AUTHORIZED_TO_ACCEPT);
        }

        if (friendShip.getFriendStatus() != FriendStatus.PENDING) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }

        friendShip.setFriendStatus(FriendStatus.ACCEPTED);
        friendShip = friendShipRepository.save(friendShip);

        publishFriendshipEvent(friendShip.getRequested(), friendShip.getReceived(), friendShip.getId(), FriendshipAction.ADDED);

        log.info("Friend request {} accepted successfully", friendshipId);
        UserSummaryResponse requester = getUserSummary(friendShip.getRequested());
        UserSummaryResponse receiver = getUserSummary(friendShip.getReceived());

        RawNotificationEvent notificationEvent = RawNotificationEvent.builder()
                .recipientId(friendShip.getRequested())
                .actorId(currentUserId)
                .actorName(receiver.fullName())
                .actorAvatar(receiver.avatar())
                .type(NotificationType.FRIEND_ACCEPT)
                .referenceId(friendShip.getId())
                .payload(Map.of("requestId", friendShip.getId()))
                .occurredAt(LocalDateTime.now())
                .build();
        rawNotificationEventPublisher.publish(notificationEvent);

        CleanupNotificationEvent cleanupEvent = CleanupNotificationEvent.builder()
                .recipientId(currentUserId)
                .referenceId(friendShip.getId())
                .type(NotificationType.FRIEND_REQUEST)
                .build();
        rawNotificationEventPublisher.publishCleanup(cleanupEvent);

        return friendShipMapper.toFriendRequestResponse(friendShip, requester, receiver);
    }

    @Override
    @Transactional
    public void declineFriendRequest(String friendshipId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("User {} declining friend request {}", currentUserId, friendshipId);

        FriendShip friendShip = friendShipRepository.findById(friendshipId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendShip.getReceived().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_AUTHORIZED_TO_DECLINE);
        }

        if (friendShip.getFriendStatus() != FriendStatus.PENDING) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }

        friendShip.setFriendStatus(FriendStatus.DECLINED);
        friendShipRepository.save(friendShip);
        publishFriendshipEvent(friendShip.getRequested(), friendShip.getReceived(), friendShip.getId(), FriendshipAction.DECLINED);
        log.info("Friend request {} declined with status DECLINED", friendshipId);

        CleanupNotificationEvent cleanupEvent = CleanupNotificationEvent.builder()
                .recipientId(currentUserId)
                .referenceId(friendShip.getId())
                .type(NotificationType.FRIEND_REQUEST)
                .build();
        rawNotificationEventPublisher.publishCleanup(cleanupEvent);
    }

    @Override
    @Transactional
    public void cancelFriendRequest(String friendshipId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("User {} canceling friend request {}", currentUserId, friendshipId);

        FriendShip friendShip = friendShipRepository.findById(friendshipId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendShip.getRequested().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_AUTHORIZED_TO_CANCEL);
        }

        if (friendShip.getFriendStatus() != FriendStatus.PENDING) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }

        friendShip.setFriendStatus(FriendStatus.CANCELLED);
        friendShipRepository.save(friendShip);
        publishFriendshipEvent(friendShip.getRequested(), friendShip.getReceived(), friendShip.getId(), FriendshipAction.CANCELLED);

        CleanupNotificationEvent cleanupEvent = CleanupNotificationEvent.builder()
                .recipientId(currentUserId)
                .referenceId(friendShip.getId())
                .type(NotificationType.FRIEND_REQUEST)
                .build();
        rawNotificationEventPublisher.publishCleanup(cleanupEvent);
        log.info("Friend request {} canceled and deleted", friendshipId);
    }

    @Override
    @Transactional
    public void unfriend(String friendId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("User {} unfriending user {}", currentUserId, friendId);

        FriendShip friendShip = friendShipRepository
                .findAcceptedFriendship(currentUserId, friendId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FRIENDS));

        friendShipRepository.delete(friendShip);

        publishFriendshipEvent(currentUserId, friendId, friendShip.getId(), FriendshipAction.REMOVED);

        log.info("Friendship between {} and {} removed", currentUserId, friendId);
    }

    private void publishFriendshipEvent(String userA, String userB, String friendshipId, FriendshipAction action) {
        FriendshipChangedEvent event = FriendshipChangedEvent.builder()
                .userA(userA)
                .userB(userB)
                .friendshipId(friendshipId)
                .action(action)
                .timestamp(System.currentTimeMillis())
                .build();

        // We use userA as aggregateId for the outbox event, but the consumer will
        // process both A and B.
        outboxEventPublisher.saveAndPublish(userA, "Friendship", EventType.FRIENDSHIP_CHANGED, event);
    }

    @Override
    public PageResponse<List<FriendRequestResponse>> getReceivedFriendRequests(Pageable pageable) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching received friend requests for user {} with pagination: {}", currentUserId, pageable);

        Page<FriendShip> requestsPage = friendShipRepository
                .findByReceivedAndFriendStatusOrderByCreatedAtDesc(currentUserId, FriendStatus.PENDING, pageable);

        List<String> requesterIds = requestsPage.getContent().stream()
                .map(FriendShip::getRequested)
                .toList();

        Map<String, UserSummaryResponse> userMap = fetchUserSummariesInBatch(requesterIds);

        return PageResponse.fromPage(requestsPage, friendShip -> {
            UserSummaryResponse requester = userMap.get(friendShip.getRequested());
            UserSummaryResponse receiver = userMap.get(friendShip.getReceived());
            return friendShipMapper.toFriendRequestResponse(friendShip, requester, receiver);
        });
    }

    @Override
    public PageResponse<List<FriendRequestResponse>> getSentFriendRequests(Pageable pageable) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching sent friend requests for user {} with pagination: {}", currentUserId, pageable);

        Page<FriendShip> requestsPage = friendShipRepository
                .findByRequestedAndFriendStatusOrderByCreatedAtDesc(currentUserId, FriendStatus.PENDING, pageable);

        List<String> receiverIds = requestsPage.getContent().stream()
                .map(FriendShip::getReceived)
                .toList();

        Map<String, UserSummaryResponse> userMap = fetchUserSummariesInBatch(receiverIds);

        return PageResponse.fromPage(requestsPage, friendShip -> {
            UserSummaryResponse requester = userMap.get(friendShip.getRequested());
            UserSummaryResponse receiver = userMap.get(friendShip.getReceived());
            return friendShipMapper.toFriendRequestResponse(friendShip, requester, receiver);
        });
    }

    @Override
    public PageResponse<List<FriendResponse>> getMyFriends(Pageable pageable) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching friends list for user {} with pagination: {} (via Neo4j)", currentUserId, pageable);

        // Get friend IDs from Neo4j (paginated)
        List<String> friendIds = graphFriendService.getFriendIdsPaginated(currentUserId, pageable);
        long totalFriends = graphFriendService.countFriends(currentUserId);

        if (friendIds.isEmpty()) {
            return PageResponse.<List<FriendResponse>>builder()
                    .data(Collections.emptyList())
                    .page(pageable.getPageNumber())
                    .totalPages((int) Math.ceil((double) totalFriends / pageable.getPageSize()))
                    .limit(pageable.getPageSize())
                    .totalItems(totalFriends)
                    .build();
        }

        // Fetch user details in batch from user-service
        Map<String, UserSummaryResponse> userMap = fetchUserSummariesInBatch(friendIds);

        // Get mutual friend counts from Neo4j in batch
        Map<String, Integer> mutualCountMap = new HashMap<>();
        for (String friendId : friendIds) {
            mutualCountMap.put(friendId, graphFriendService.getMutualFriendsCount(currentUserId, friendId));
        }

        List<FriendResponse> friends = friendIds.stream()
                .map(friendId -> {
                    UserSummaryResponse user = userMap.get(friendId);
                    if (user == null) return null;
                    Integer mutualCount = mutualCountMap.getOrDefault(friendId, 0);
                    return FriendResponse.builder()
                            .userId(friendId)
                            .userName(user.fullName())
                            .userAvatar(user.avatar())
                            .userPhone(user.phoneNumber())
                            .mutualFriendsCount(mutualCount)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        return PageResponse.<List<FriendResponse>>builder()
                .data(friends)
                .page(pageable.getPageNumber())
                .totalPages((int) Math.ceil((double) totalFriends / pageable.getPageSize()))
                .limit(pageable.getPageSize())
                .totalItems(totalFriends)
                .build();
    }

    @Override
    public FriendshipStatusResponse checkFriendshipStatus(String userId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Checking friendship status between {} and {}", currentUserId, userId);

        if (currentUserId == null || currentUserId.isEmpty() || userId == null || userId.isEmpty()) {
            log.warn("Invalid userId for friendship check: currentUserId={}, userId={}", currentUserId, userId);
            return FriendshipStatusResponse.builder()
                    .areFriends(false)
                    .status(null)
                    .friendshipId(null)
                    .requestedBy(null)
                    .build();
        }

        Optional<FriendShip> friendship = friendShipRepository
                .findFriendshipBetweenUsers(currentUserId, userId);

        if (friendship.isEmpty()) {
            return FriendshipStatusResponse.builder()
                    .areFriends(false)
                    .status(null)
                    .friendshipId(null)
                    .requestedBy(null)
                    .build();
        }

        FriendShip fs = friendship.get();
        return FriendshipStatusResponse.builder()
                .areFriends(fs.getFriendStatus() == FriendStatus.ACCEPTED)
                .status(fs.getFriendStatus())
                .friendshipId(fs.getId())
                .requestedBy(fs.getRequested())
                .build();
    }

    @Override
    public MutualFriendsResponse getMutualFriends(String userId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching mutual friends between {} and {} (via Neo4j)", currentUserId, userId);

        List<String> mutualFriendIds = graphFriendService.getMutualFriendIds(currentUserId, userId);

        Map<String, UserSummaryResponse> userMap = fetchUserSummariesInBatch(mutualFriendIds);

        List<FriendResponse> mutualFriends = mutualFriendIds.stream()
                .map(friendId -> {
                    UserSummaryResponse user = userMap.get(friendId);
                    if (user == null) return null;
                    return friendShipMapper.toFriendResponseFromUser(user);
                })
                .filter(Objects::nonNull)
                .toList();

        return MutualFriendsResponse.builder()
                .count(mutualFriends.size())
                .mutualFriends(mutualFriends)
                .build();
    }

    @Override
    public Integer getMutualFriendsCount(String userId) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Counting mutual friends between {} and {} (via Neo4j)", currentUserId, userId);
        return graphFriendService.getMutualFriendsCount(currentUserId, userId);
    }

    @Override
    public Set<String> getFriendIds(String userId) {
        log.info("Fetching friend IDs for user {} (via Neo4j)", userId);
        return new HashSet<>(graphFriendService.getFriendIds(userId));
    }

    @Override
    public PageResponse<List<FriendSuggestionResponse>> getGraphSuggestions(Pageable pageable) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching graph-based friend suggestions for user {} with pagination: {}", currentUserId, pageable);
        return graphFriendService.getFriendSuggestionsFromGraph(currentUserId, pageable);
    }

    @Override
    public PageResponse<List<FriendSuggestionResponse>> getContactSuggestions(Pageable pageable) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Fetching contact-based friend suggestions for user {} with pagination: {}", currentUserId, pageable);
        return graphFriendService.getContactSuggestions(currentUserId, pageable);
    }

    @Override
    public Map<String, String> batchCheckFriendshipStatus(List<String> targetUserIds) {
        String currentUserId = securityUtil.getCurrentUserId();

        // Lọc các ObjectId hợp lệ để tránh lỗi ConversionFailedException với các ID tĩnh như ai-assistant-001
        List<String> validTargetIds = targetUserIds.stream()
                .filter(id -> id != null && id.length() == 24 && id.matches("^[0-9a-fA-F]+$"))
                .collect(Collectors.toList());

        List<FriendShip> friendships = new ArrayList<>();
        if (!validTargetIds.isEmpty()) {
            friendships = friendShipRepository.findFriendshipsBetweenUserAndTargets(currentUserId, validTargetIds);
        }

        Map<String, String> result = new HashMap<>();
        for (String id : targetUserIds) {
            result.put(id, null);
        }

        for (FriendShip fs : friendships) {
            String targetId = fs.getRequested().equals(currentUserId) ? fs.getReceived() : fs.getRequested();
            result.put(targetId, fs.getFriendStatus().name());
        }
        return result;
    }

    // Helper methods

    private Set<String> extractFriendIds(List<FriendShip> friendships, String userId) {
        return friendships.stream()
                .map(fs -> fs.getRequested().equals(userId) ? fs.getReceived() : fs.getRequested())
                .collect(Collectors.toSet());
    }

    private UserSummaryResponse getUserSummary(String userId) {
        try {
            ApiResponse<UserSummaryResponse> response = userServiceClient.getUserSummary(userId);
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.error("Failed to fetch user summary for userId: {}", userId, e);
        }

        return UserSummaryResponse.builder()
                .id(userId)
                .fullName("Unknown User")
                .avatar(null)
                .build();
    }

    private void validateUserExists(String userId) {
        try {
            ApiResponse<UserSummaryResponse> response = userServiceClient.getUserSummary(userId);
            if (response == null || response.data() == null) {
                throw new AppException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (Exception e) {
            log.error("Failed to validate user: {}", userId, e);
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private Map<String, UserSummaryResponse> fetchUserSummariesInBatch(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        try {
            ApiResponse<Map<String, UserSummaryResponse>> response = userServiceClient.getUsersByIds(userIds);
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.error("Failed to batch fetch user summaries: {}", userIds, e);
        }

        return userIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> UserSummaryResponse.builder()
                                .id(id)
                                .fullName("Unknown User")
                                .avatar(null)
                                .build()));
    }
}
