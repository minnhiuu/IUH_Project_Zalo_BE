package com.bondhub.friendservice.graph.service;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.friendservice.dto.response.FriendSuggestionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface GraphFriendService {

    List<String> getFriendIds(String userId);

    List<String> getFriendIdsPaginated(String userId, Pageable pageable);

    long countFriends(String userId);

    List<String> getMutualFriendIds(String userA, String userB);

    int getMutualFriendsCount(String userA, String userB);

    PageResponse<List<FriendSuggestionResponse>> getFriendSuggestionsFromGraph(String userId, Pageable pageable);

    PageResponse<List<FriendSuggestionResponse>> getContactSuggestions(String userId, Pageable pageable);

    void ensureUserNode(String userId);

    void createFriendRelationship(String userA, String userB);

    void removeFriendRelationship(String userA, String userB);

    void mergeInContact(String fromUserId, String toUserId, double score, String source);

    void deleteUserNode(String userId);

    PageResponse<List<FriendSuggestionResponse>> getUnifiedSuggestions(String userId, Pageable pageable);
}
