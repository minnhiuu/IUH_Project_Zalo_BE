package com.bondhub.common.dto.client.friendservice;

import lombok.Builder;

@Builder
public record UserSearchContextResponse(
        String userId,
        String friendshipId,
        String friendshipStatus,
        String requestedBy,
        Boolean blockedByMe,
        Boolean blockedMe,
        Integer mutualFriendsCount,
        Integer sharedGroupsCount,
        Boolean inContact,
        Double contactScore
) {
}
