package com.bondhub.friendservice.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record MutualFriendsResponse(
    Integer count,
    List<FriendResponse> mutualFriends
) {}
