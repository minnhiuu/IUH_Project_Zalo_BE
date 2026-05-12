package com.bondhub.searchservice.dto.response;

import lombok.Builder;

@Builder
public record UserSearchResponse(
        String id,
        String fullName,
        String avatar,
        String phoneNumber,
        String friendshipId,
        String friendshipStatus,
        String requestedBy,
        String relationshipLabel,
        Integer mutualFriendsCount,
        Integer sharedGroupsCount,
        Boolean inContact,
        UserSearchScoreBreakdown debug
) {
}
