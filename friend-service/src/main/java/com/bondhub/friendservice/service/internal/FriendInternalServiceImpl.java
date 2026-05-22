package com.bondhub.friendservice.service.internal;

import com.bondhub.common.dto.client.friendservice.UserSearchContextRequest;
import com.bondhub.common.dto.client.friendservice.UserSearchContextResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.friendservice.model.BlockList;
import com.bondhub.friendservice.model.FriendShip;
import com.bondhub.friendservice.model.enums.FriendStatus;
import com.bondhub.friendservice.graph.dto.UserSearchGraphMetrics;
import com.bondhub.friendservice.graph.service.GraphFriendService;
import com.bondhub.friendservice.repository.BlockListRepository;
import com.bondhub.friendservice.repository.FriendShipRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendInternalServiceImpl implements FriendInternalService {

    static final String FRIENDSHIP_STATUS_NONE = "NONE";

    SecurityUtil securityUtil;
    FriendShipRepository friendShipRepository;
    BlockListRepository blockListRepository;
    GraphFriendService graphFriendService;

    @Override
    public List<UserSearchContextResponse> getUserSearchContext(UserSearchContextRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();

        if (request == null || request.targetUserIds() == null || request.targetUserIds().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> targetIds = sanitizeTargets(request.targetUserIds(), currentUserId);
        if (targetIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, FriendShip> friendshipMap = fetchFriendships(currentUserId, targetIds);
        Set<String> blockedByMe = fetchBlockedByMe(currentUserId);
        Set<String> blockedMe = fetchBlockedMe(currentUserId);
        Map<String, UserSearchGraphMetrics> graphMetrics = graphFriendService.getUserSearchGraphMetrics(currentUserId, targetIds);

        return targetIds.stream()
                .map(targetId -> toUserSearchContextResponse(
                        targetId,
                        friendshipMap.get(targetId),
                        blockedByMe.contains(targetId),
                        blockedMe.contains(targetId),
                        graphMetrics.getOrDefault(targetId, UserSearchGraphMetrics.empty(targetId))))
                .toList();
    }

    private List<String> sanitizeTargets(List<String> targetUserIds, String currentUserId) {
        return targetUserIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .filter(id -> !id.equals(currentUserId))
                .distinct()
                .toList();
    }

    private Map<String, FriendShip> fetchFriendships(String currentUserId, List<String> targetIds) {
        List<String> mongoTargetIds = targetIds.stream()
                .filter(this::isMongoObjectId)
                .toList();

        if (!isMongoObjectId(currentUserId) || mongoTargetIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return friendShipRepository.findFriendshipsBetweenUserAndTargets(currentUserId, mongoTargetIds).stream()
                .filter(friendShip -> friendShip.getFriendStatus() == FriendStatus.ACCEPTED
                        || friendShip.getFriendStatus() == FriendStatus.PENDING)
                .collect(Collectors.toMap(
                        friendShip -> resolveTargetId(friendShip, currentUserId),
                        friendShip -> friendShip,
                        this::chooseHigherPriorityFriendship));
    }

    private Set<String> fetchBlockedByMe(String currentUserId) {
        if (!isMongoObjectId(currentUserId)) {
            return Collections.emptySet();
        }

        return blockListRepository.findByBlockerId(currentUserId).stream()
                .map(BlockList::getBlockedUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<String> fetchBlockedMe(String currentUserId) {
        if (!isMongoObjectId(currentUserId)) {
            return Collections.emptySet();
        }

        return blockListRepository.findByBlockedUserId(currentUserId).stream()
                .map(BlockList::getBlockerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private UserSearchContextResponse toUserSearchContextResponse(
            String targetId,
            FriendShip friendship,
            boolean blockedByMe,
            boolean blockedMe,
            UserSearchGraphMetrics graphMetrics) {

        FriendStatus status = friendship != null ? friendship.getFriendStatus() : null;
        UserSearchGraphMetrics safeGraphMetrics = graphMetrics != null
                ? graphMetrics
                : UserSearchGraphMetrics.empty(targetId);

        return UserSearchContextResponse.builder()
                .userId(targetId)
                .friendshipId(friendship != null ? friendship.getId() : null)
                .friendshipStatus(status != null ? status.name() : FRIENDSHIP_STATUS_NONE)
                .requestedBy(status == FriendStatus.PENDING ? friendship.getRequested() : null)
                .blockedByMe(blockedByMe)
                .blockedMe(blockedMe)
                .mutualFriendsCount(safeGraphMetrics.mutualFriendsCount())
                .sharedGroupsCount(safeGraphMetrics.sharedGroupsCount())
                .inContact(safeGraphMetrics.inContact())
                .contactScore(safeGraphMetrics.contactScore())
                .build();
    }

    private String resolveTargetId(FriendShip friendShip, String currentUserId) {
        return friendShip.getRequested().equals(currentUserId)
                ? friendShip.getReceived()
                : friendShip.getRequested();
    }

    private FriendShip chooseHigherPriorityFriendship(FriendShip existing, FriendShip candidate) {
        if (candidate.getFriendStatus() == FriendStatus.ACCEPTED) {
            return candidate;
        }
        return existing;
    }

    private boolean isMongoObjectId(String value) {
        return value != null && value.length() == 24 && value.matches("^[0-9a-fA-F]+$");
    }
}
