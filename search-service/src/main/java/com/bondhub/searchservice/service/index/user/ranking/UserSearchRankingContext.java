package com.bondhub.searchservice.service.index.user.ranking;

/**
 * Ranking-only context for user search.
 * Contains signals collected from ES, Friend Service, Message Service,
 * and SocialFeed Service before calculating final ranking score.
 */
public record UserSearchRankingContext(
        String friendshipStatus,
        String requestedBy,
        int mutualFriendsCount,
        int sharedGroupsCount,
        boolean inContact,
        double contactScore,
        boolean exactPhoneMatch,

        /**
         * Recent interaction score from chat/social feed.
         * Expected range: 0.0 - 5.0.
         */
        double recentInteractionScore
) {
    public static UserSearchRankingContext empty() {
        return new UserSearchRankingContext(
                null,
                null,
                0,
                0,
                false,
                0.0,
                false,
                0.0
        );
    }
}
