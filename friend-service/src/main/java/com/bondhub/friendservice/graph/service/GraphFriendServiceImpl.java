package com.bondhub.friendservice.graph.service;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.friendservice.client.UserServiceClient;
import com.bondhub.friendservice.dto.response.FriendSuggestionResponse;
import com.bondhub.friendservice.graph.repository.UserNodeRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class GraphFriendServiceImpl implements GraphFriendService {

    UserNodeRepository userNodeRepository;
    UserServiceClient userServiceClient;

    @Override
    public List<String> getFriendIds(String userId) {
        log.debug("Neo4j: Getting friend IDs for user {}", userId);
        return userNodeRepository.findFriendIds(userId);
    }

    @Override
    public List<String> getFriendIdsPaginated(String userId, Pageable pageable) {
        long skip = pageable.getOffset();
        int limit = pageable.getPageSize();
        log.debug("Neo4j: Getting friend IDs for user {} (skip={}, limit={})", userId, skip, limit);
        return userNodeRepository.findFriendIdsPaginated(userId, skip, limit);
    }

    @Override
    public long countFriends(String userId) {
        return userNodeRepository.countFriends(userId);
    }

    @Override
    public List<String> getMutualFriendIds(String userA, String userB) {
        log.debug("Neo4j: Getting mutual friend IDs between {} and {}", userA, userB);
        return userNodeRepository.findMutualFriendIds(userA, userB);
    }

    @Override
    public int getMutualFriendsCount(String userA, String userB) {
        return userNodeRepository.countMutualFriends(userA, userB);
    }

    @Override
    public PageResponse<List<FriendSuggestionResponse>> getFriendSuggestionsFromGraph(String userId, Pageable pageable) {
        long skip = pageable.getOffset();
        int limit = pageable.getPageSize();
        log.info("Neo4j: Getting friend suggestions for user {} (skip={}, limit={})", userId, skip, limit);

        List<Map<String, Object>> results = userNodeRepository.findFriendSuggestions(userId, skip, limit);
        long total = userNodeRepository.countFriendSuggestions(userId);
        List<FriendSuggestionResponse> data = enrichSuggestions(results, true);

        return PageResponse.<List<FriendSuggestionResponse>>builder()
                .data(data)
                .page(pageable.getPageNumber())
                .totalPages((int) Math.ceil((double) total / pageable.getPageSize()))
                .limit(pageable.getPageSize())
                .totalItems(total)
                .build();
    }

    @Override
    public PageResponse<List<FriendSuggestionResponse>> getContactSuggestions(String userId, Pageable pageable) {
        long skip = pageable.getOffset();
        int limit = pageable.getPageSize();
        log.info("Neo4j: Getting contact suggestions for user {} (skip={}, limit={})", userId, skip, limit);

        List<Map<String, Object>> results = userNodeRepository.findContactSuggestions(userId, skip, limit);
        long total = userNodeRepository.countContactSuggestions(userId);
        List<FriendSuggestionResponse> data = enrichSuggestions(results, false);

        return PageResponse.<List<FriendSuggestionResponse>>builder()
                .data(data)
                .page(pageable.getPageNumber())
                .totalPages((int) Math.ceil((double) total / pageable.getPageSize()))
                .limit(pageable.getPageSize())
                .totalItems(total)
                .build();
    }

    @Override
    public void ensureUserNode(String userId) {
        userNodeRepository.mergeUser(userId);
    }

    @Override
    public void createFriendRelationship(String userA, String userB) {
        log.info("Neo4j: Creating FRIEND relationship {} <-> {}", userA, userB);
        userNodeRepository.createFriendRelationship(userA, userB);
    }

    @Override
    public void removeFriendRelationship(String userA, String userB) {
        log.info("Neo4j: Removing FRIEND relationship {} <-> {}", userA, userB);
        userNodeRepository.removeFriendRelationship(userA, userB);
    }

    @Override
    public void mergeInContact(String fromUserId, String toUserId, double score, String source) {
        log.debug("Neo4j: Merging IN_CONTACT {} -> {} (score={}, source={})", fromUserId, toUserId, score, source);
        userNodeRepository.mergeInContactRelationship(fromUserId, toUserId, score, source);
    }

    @Override
    public void deleteUserNode(String userId) {
        log.info("Neo4j: Deleting user node and all relationships for {}", userId);
        userNodeRepository.deleteUserNode(userId);
    }

    @Override
    public PageResponse<List<FriendSuggestionResponse>> getUnifiedSuggestions(String userId, Pageable pageable) {
        long skip = pageable.getOffset();
        int limit = pageable.getPageSize();
        log.info("Neo4j: Getting unified suggestions for user {} (skip={}, limit={})", userId, skip, limit);

        List<Map<String, Object>> results = userNodeRepository.findUnifiedSuggestions(userId, skip, limit);
        long total = userNodeRepository.countUnifiedSuggestions(userId);

        List<FriendSuggestionResponse> data = enrichUnifiedSuggestions(results);

        return PageResponse.<List<FriendSuggestionResponse>>builder()
                .data(data)
                .page(pageable.getPageNumber())
                .totalPages((int) Math.ceil((double) total / pageable.getPageSize()))
                .limit(pageable.getPageSize())
                .totalItems(total)
                .build();
    }

    // ===== Private helpers =====

    private List<FriendSuggestionResponse> enrichSuggestions(List<Map<String, Object>> results, boolean isGraphSuggestion) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> normalized = results.stream()
            .map(this::normalizeRow)
            .toList();

        List<String> userIds = normalized.stream()
                .map(r -> (String) r.get("userId"))
                .filter(Objects::nonNull)
                .toList();

        Map<String, UserSummaryResponse> userMap = fetchUserSummaries(userIds);

        return normalized.stream()
                .map(r -> {
                    String uid = (String) r.get("userId");
                    UserSummaryResponse user = userMap.get(uid);
                    if (user == null) return null;

                    return FriendSuggestionResponse.builder()
                            .userId(uid)
                            .fullName(user.fullName())
                            .avatar(user.avatar())
                            .phoneNumber(user.phoneNumber())
                            .mutualFriendsCount(isGraphSuggestion ? ((Number) r.get("mutualCount")).intValue() : null)
                            .contactScore(!isGraphSuggestion ? ((Number) r.get("score")).doubleValue() : null)
                            .sharedGroupsCount(null)
                            .totalScore(null)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, UserSummaryResponse> fetchUserSummaries(List<String> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        try {
            ApiResponse<Map<String, UserSummaryResponse>> response = userServiceClient.getUsersByIds(userIds);
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.error("Failed to batch fetch user summaries for suggestions: {}", userIds, e);
        }
        return Collections.emptyMap();
    }

    private List<FriendSuggestionResponse> enrichUnifiedSuggestions(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> normalized = results.stream()
            .map(this::normalizeRow)
            .toList();

        List<String> userIds = normalized.stream()
                .map(r -> (String) r.get("userId"))
                .filter(Objects::nonNull)
                .toList();

        Map<String, UserSummaryResponse> userMap = fetchUserSummaries(userIds);

        return normalized.stream()
                .map(r -> {
                    String uid = (String) r.get("userId");
                    UserSummaryResponse user = userMap.get(uid);
                    if (user == null) return null;

                    return FriendSuggestionResponse.builder()
                            .userId(uid)
                            .fullName(user.fullName())
                            .avatar(user.avatar())
                            .phoneNumber(user.phoneNumber())
                            .mutualFriendsCount(((Number) r.get("mutualFriendsCount")).intValue())
                            .sharedGroupsCount(((Number) r.get("sharedGroupsCount")).intValue())
                            .contactScore(((Number) r.get("contactScore")).doubleValue())
                            .totalScore(((Number) r.get("totalScore")).doubleValue())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        if (row == null) {
            return Collections.emptyMap();
        }
        Object nested = row.get("row");
        if (nested instanceof Map<?, ?> nestedMap) {
            return (Map<String, Object>) nestedMap;
        }
        return row;
    }
}
