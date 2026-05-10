package com.bondhub.searchservice.service.index.user.ranking;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserSearchRankingStrategy {

    public static final String FRIEND_LABEL_KEY = "search.user.relationship.friend";
    public static final String PENDING_RECEIVED_LABEL_KEY = "search.user.relationship.pending.received";
    public static final String PENDING_SENT_LABEL_KEY = "search.user.relationship.pending.sent";
    public static final String MUTUAL_FRIENDS_LABEL_KEY = "search.user.relationship.mutual_friends";
    public static final String CONTACT_LABEL_KEY = "search.user.relationship.contact";

    public static final double EXACT_PHONE_MATCH_BOOST = 10_000.0;
    public static final double ES_SCORE_MULTIPLIER = 100.0;
    public static final double FRIEND_BOOST = 1_000.0;
    public static final double PENDING_RECEIVED_BOOST = 800.0;
    public static final double PENDING_SENT_BOOST = 500.0;
    public static final double MUTUAL_FRIEND_WEIGHT = 30.0;
    public static final int MUTUAL_FRIEND_CAP = 10;
    public static final double SHARED_GROUP_WEIGHT = 15.0;
    public static final int SHARED_GROUP_CAP = 10;
    public static final double CONTACT_SCORE_WEIGHT = 50.0;
    public static final double CONTACT_SCORE_CAP = 5.0;

    private static final String ACCEPTED = "ACCEPTED";
    private static final String PENDING = "PENDING";

    public double calculateFinalScore(double elasticsearchScore, UserSearchRankingContext context, String currentUserId) {
        UserSearchRankingContext safeContext = context != null ? context : UserSearchRankingContext.empty();

        return exactPhoneBoost(safeContext)
                + normalizedElasticsearchScore(elasticsearchScore)
                + relationshipBoost(safeContext, currentUserId)
                + graphBoost(safeContext)
                + contactBoost(safeContext);
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
        return context.exactPhoneMatch() ? EXACT_PHONE_MATCH_BOOST : 0.0;
    }

    private double normalizedElasticsearchScore(double elasticsearchScore) {
        return Math.max(elasticsearchScore, 0.0) * ES_SCORE_MULTIPLIER;
    }

    private double relationshipBoost(UserSearchRankingContext context, String currentUserId) {
        if (isAccepted(context.friendshipStatus())) {
            return FRIEND_BOOST;
        }

        if (isPending(context.friendshipStatus())) {
            return isRequestedByCurrentUser(context.requestedBy(), currentUserId)
                    ? PENDING_SENT_BOOST
                    : PENDING_RECEIVED_BOOST;
        }

        return 0.0;
    }

    private double graphBoost(UserSearchRankingContext context) {
        int mutualFriends = Math.min(Math.max(context.mutualFriendsCount(), 0), MUTUAL_FRIEND_CAP);
        int sharedGroups = Math.min(Math.max(context.sharedGroupsCount(), 0), SHARED_GROUP_CAP);

        return mutualFriends * MUTUAL_FRIEND_WEIGHT
                + sharedGroups * SHARED_GROUP_WEIGHT;
    }

    private double contactBoost(UserSearchRankingContext context) {
        if (!context.inContact()) {
            return 0.0;
        }

        double cappedScore = Math.min(Math.max(context.contactScore(), 0.0), CONTACT_SCORE_CAP);
        return cappedScore * CONTACT_SCORE_WEIGHT;
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

        return value.replaceAll("\\D", "");
    }
}
