package com.bondhub.searchservice.service.index.user.ranking;

import com.bondhub.common.utils.PhoneUtil;
import com.bondhub.searchservice.config.SearchRankingProperties;
import com.bondhub.searchservice.dto.response.UserSearchScoreBreakdown;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSearchRankingStrategy {

    public static final String FRIEND_LABEL_KEY = "search.user.relationship.friend";
    public static final String PENDING_RECEIVED_LABEL_KEY = "search.user.relationship.pending.received";
    public static final String PENDING_SENT_LABEL_KEY = "search.user.relationship.pending.sent";
    public static final String MUTUAL_FRIENDS_LABEL_KEY = "search.user.relationship.mutual_friends";
    public static final String CONTACT_LABEL_KEY = "search.user.relationship.contact";

    private static final String ACCEPTED = "ACCEPTED";
    private static final String PENDING = "PENDING";

    SearchRankingProperties searchRankingProperties;

    public double calculateFinalScore(double elasticsearchScore, UserSearchRankingContext context, String currentUserId) {
        return calculateScoreBreakdown(elasticsearchScore, context, currentUserId).finalScore();
    }

    public UserSearchScoreBreakdown calculateScoreBreakdown(
            double elasticsearchScore,
            UserSearchRankingContext context,
            String currentUserId) {
        UserSearchRankingContext safeContext = context != null ? context : UserSearchRankingContext.empty();
        double exactPhoneBoost = exactPhoneBoost(safeContext);
        double esScoreBoost = normalizedElasticsearchScore(elasticsearchScore);
        double relationshipBoost = relationshipBoost(safeContext, currentUserId);
        double graphBoost = graphBoost(safeContext);
        double contactBoost = contactBoost(safeContext);
        double recentInteractionBoost = recentInteractionBoost(safeContext);
        double finalScore = exactPhoneBoost
                + esScoreBoost
                + relationshipBoost
                + graphBoost
                + contactBoost
                + recentInteractionBoost;

        return UserSearchScoreBreakdown.builder()
                .esScore(elasticsearchScore)
                .exactPhoneBoost(exactPhoneBoost)
                .esScoreBoost(esScoreBoost)
                .relationshipBoost(relationshipBoost)
                .graphBoost(graphBoost)
                .contactBoost(contactBoost)
                .recentInteractionBoost(recentInteractionBoost)
                .finalScore(finalScore)
                .build();
    }

    public UserSearchRelationshipLabel resolveRelationshipLabel(UserSearchRankingContext context, String currentUserId) {
        if (context == null) {
            return null;
        }

        if (isAccepted(context.friendshipStatus())) {
            return UserSearchRelationshipLabel.of(FRIEND_LABEL_KEY);
        }

        if (isPending(context.friendshipStatus())) {
            return isRequestedByCurrentUser(context.requestedBy(), currentUserId)
                    ? UserSearchRelationshipLabel.of(PENDING_SENT_LABEL_KEY)
                    : UserSearchRelationshipLabel.of(PENDING_RECEIVED_LABEL_KEY);
        }

        if (context.mutualFriendsCount() > 0) {
            return UserSearchRelationshipLabel.of(MUTUAL_FRIENDS_LABEL_KEY, context.mutualFriendsCount());
        }

        if (context.inContact()) {
            return UserSearchRelationshipLabel.of(CONTACT_LABEL_KEY);
        }

        return null;
    }

    public boolean isExactPhoneMatch(String keyword, String phoneNumber) {
        String normalizedKeyword = normalizePhone(keyword);
        String normalizedPhone = normalizePhone(phoneNumber);
        return StringUtils.hasText(normalizedKeyword)
                && StringUtils.hasText(normalizedPhone)
                && normalizedKeyword.equals(normalizedPhone);
    }

    private double exactPhoneBoost(UserSearchRankingContext context) {
        return context.exactPhoneMatch() ? userRanking().getExactPhoneBoost() : 0.0;
    }

    private double normalizedElasticsearchScore(double elasticsearchScore) {
        return Math.max(elasticsearchScore, 0.0) * userRanking().getEsScoreMultiplier();
    }

    private double relationshipBoost(UserSearchRankingContext context, String currentUserId) {
        if (isAccepted(context.friendshipStatus())) {
            return userRanking().getAcceptedFriendBoost();
        }

        if (isPending(context.friendshipStatus())) {
            return isRequestedByCurrentUser(context.requestedBy(), currentUserId)
                    ? userRanking().getPendingSentBoost()
                    : userRanking().getPendingReceivedBoost();
        }

        return 0.0;
    }

    private double graphBoost(UserSearchRankingContext context) {
        SearchRankingProperties.User userRanking = userRanking();
        int mutualFriends = Math.min(Math.max(context.mutualFriendsCount(), 0), userRanking.getMutualFriendCap());
        int sharedGroups = Math.min(Math.max(context.sharedGroupsCount(), 0), userRanking.getSharedGroupCap());

        return mutualFriends * userRanking.getMutualFriendWeight()
                + sharedGroups * userRanking.getSharedGroupWeight();
    }

    private double contactBoost(UserSearchRankingContext context) {
        if (!context.inContact()) {
            return 0.0;
        }

        SearchRankingProperties.User userRanking = userRanking();
        double cappedScore = Math.min(Math.max(context.contactScore(), 0.0), userRanking.getContactScoreCap());
        return cappedScore * userRanking.getContactScoreWeight();
    }

    private double recentInteractionBoost(UserSearchRankingContext context) {
        SearchRankingProperties.User userRanking = userRanking();
        double cappedScore = Math.min(Math.max(context.recentInteractionScore(), 0.0), userRanking.getRecentInteractionCap());
        return cappedScore * userRanking.getRecentInteractionWeight();
    }

    private SearchRankingProperties.User userRanking() {
        return searchRankingProperties.getUser();
    }

    private boolean isAccepted(String status) {
        return ACCEPTED.equalsIgnoreCase(status);
    }

    private boolean isPending(String status) {
        return PENDING.equalsIgnoreCase(status);
    }

    private boolean isRequestedByCurrentUser(String requestedBy, String currentUserId) {
        return StringUtils.hasText(currentUserId) && currentUserId.equals(requestedBy);
    }

    private String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return PhoneUtil.normalizeVnPhone(value)
                .orElseGet(() -> value.replaceAll("\\D", ""));
    }
}
