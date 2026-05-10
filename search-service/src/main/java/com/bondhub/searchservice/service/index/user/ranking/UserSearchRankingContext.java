package com.bondhub.searchservice.service.index.user.ranking;

public record UserSearchRankingContext(
        String friendshipStatus,
        String requestedBy,
        int mutualFriendsCount,
        int sharedGroupsCount,
        boolean inContact,
        double contactScore,
        boolean exactPhoneMatch
) {
    public static UserSearchRankingContext empty() {
        return new UserSearchRankingContext(null, null, 0, 0, false, 0.0, false);
    }
}
