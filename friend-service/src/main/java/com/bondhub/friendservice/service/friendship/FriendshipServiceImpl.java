package com.bondhub.friendservice.service.friendship;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.friendservice.client.UserServiceClient;
import com.bondhub.friendservice.dto.request.FriendRequestSendRequest;
import com.bondhub.friendservice.dto.response.*;
import com.bondhub.friendservice.mapper.FriendShipMapper;
import com.bondhub.friendservice.model.FriendShip;
import com.bondhub.friendservice.model.enums.FriendStatus;
import com.bondhub.friendservice.repository.FriendShipRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
        log.info("Friend request created with id: {}", friendShip.getId());

        UserSummaryResponse requester = getUserSummary(friendShip.getRequested());
        UserSummaryResponse receiver = getUserSummary(friendShip.getReceived());
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

        log.info("Friend request {} accepted successfully", friendshipId);
        UserSummaryResponse requester = getUserSummary(friendShip.getRequested());
        UserSummaryResponse receiver = getUserSummary(friendShip.getReceived());
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
        log.info("Friend request {} declined with status DECLINED", friendshipId);
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
        log.info("Friendship between {} and {} removed", currentUserId, friendId);
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
        log.info("Fetching friends list for user {} with pagination: {}", currentUserId, pageable);

        Page<FriendShip> friendshipsPage = friendShipRepository.findAllFriendsByUserId(currentUserId, pageable);

        List<String> friendIds = friendshipsPage.getContent().stream()
                .map(friendship -> friendship.getRequested().equals(currentUserId)
                        ? friendship.getReceived()
                        : friendship.getRequested())
                .toList();

        Map<String, UserSummaryResponse> userMap = fetchUserSummariesInBatch(friendIds);

        Map<String, Long> mutualFriendsMap = calculateMutualFriendsInBatch(currentUserId, friendIds);

        return PageResponse.fromPage(friendshipsPage, friendship -> {
            String friendId = friendship.getRequested().equals(currentUserId)
                    ? friendship.getReceived()
                    : friendship.getRequested();
            UserSummaryResponse friend = userMap.get(friendId);
            Integer mutualCount = mutualFriendsMap.getOrDefault(friendId, 0L).intValue();
            return friendShipMapper.toFriendResponse(friend, friendship, mutualCount);
        });
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
        log.info("Fetching mutual friends with user {}", userId);

        Set<String> mutualFriendIds = findMutualFriendIds(userId);

        List<FriendResponse> mutualFriends = mutualFriendIds.stream()
                .map(friendId -> {
                    UserSummaryResponse user = getUserSummary(friendId);
                    return friendShipMapper.toFriendResponseFromUser(user);
                })
                .toList();

        return MutualFriendsResponse.builder()
                .count(mutualFriends.size())
                .mutualFriends(mutualFriends)
                .build();
    }

    @Override
    public Integer getMutualFriendsCount(String userId) {
        log.info("Counting mutual friends with user {}", userId);
        return findMutualFriendIds(userId).size();
    }

    private Set<String> findMutualFriendIds(String targetUserId) {
        String currentUserId = securityUtil.getCurrentUserId();

        List<FriendShip> currentUserFriends = friendShipRepository.findAllFriendsByUserId(currentUserId);
        Set<String> currentUserFriendIds = extractFriendIds(currentUserFriends, currentUserId);

        List<FriendShip> targetUserFriends = friendShipRepository.findAllFriendsByUserId(targetUserId);
        Set<String> targetUserFriendIds = extractFriendIds(targetUserFriends, targetUserId);

        Set<String> mutualFriendIds = new HashSet<>(currentUserFriendIds);
        mutualFriendIds.retainAll(targetUserFriendIds);

        return mutualFriendIds;
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
                                .build()
                ));
    }

    private Map<String, Long> calculateMutualFriendsInBatch(String currentUserId, List<String> friendIds) {
        String currentUserIdStr = currentUserId;

        List<FriendShip> currentUserFriendships = friendShipRepository.findAllFriendsByUserId(currentUserIdStr);
        Set<String> currentUserFriendSet = extractFriendIds(currentUserFriendships, currentUserIdStr);

        Map<String, Long> mutualCountMap = new HashMap<>();

        for (String friendId : friendIds) {
            List<FriendShip> targetFriendships = friendShipRepository.findAllFriendsByUserId(friendId);
            Set<String> targetFriendSet = extractFriendIds(targetFriendships, friendId);

            long mutualCount = targetFriendSet.stream()
                    .filter(currentUserFriendSet::contains)
                    .count();

            mutualCountMap.put(friendId, mutualCount);
        }

        return mutualCountMap;
    }
}
