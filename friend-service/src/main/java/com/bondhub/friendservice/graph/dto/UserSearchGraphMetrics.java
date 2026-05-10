package com.bondhub.friendservice.graph.dto;

import lombok.Builder;

@Builder
public record UserSearchGraphMetrics(
        String userId,
        Integer mutualFriendsCount,
        Integer sharedGroupsCount,
        Boolean inContact,
        Double contactScore
) {
    public static UserSearchGraphMetrics empty(String userId) {
        return UserSearchGraphMetrics.builder()
                .userId(userId)
                .mutualFriendsCount(0)
                .sharedGroupsCount(0)
                .inContact(false)
                .contactScore(0.0)
                .build();
    }
}
