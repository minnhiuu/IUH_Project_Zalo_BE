package com.bondhub.friendservice.dto.response;

import lombok.Builder;

@Builder
public record FriendSuggestionResponse(
    String userId,
    String fullName,
    String avatar,
    String phoneNumber,
    Integer mutualFriendsCount,
    Integer sharedGroupsCount,
    Double contactScore,
    Double totalScore
) {}
