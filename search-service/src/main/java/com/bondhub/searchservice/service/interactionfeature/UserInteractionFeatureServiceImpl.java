package com.bondhub.searchservice.service.interactionfeature;

import com.bondhub.common.event.search.ChatInteractionOccurredEvent;
import com.bondhub.common.event.search.SocialFeedInteractionOccurredEvent;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.searchservice.model.mongodb.UserInteractionFeature;
import com.bondhub.searchservice.repository.mongodb.UserInteractionFeatureRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInteractionFeatureServiceImpl implements UserInteractionFeatureService {

    private static final double MAX_INTERACTION_SCORE = 5.0;
    private static final double CHAT_MESSAGE_WEIGHT = 0.25;
    private static final double VIEW_WEIGHT = 0.2;
    private static final double REACTION_WEIGHT = 1.0;
    private static final double COMMENT_WEIGHT = 2.0;
    private static final double DISLIKE_WEIGHT = 2.0;
    private static final double RECENCY_DECAY_DAYS = 7.0;

    UserInteractionFeatureRepository userInteractionFeatureRepository;

    @Override
    @Transactional
    public void recordChatInteraction(ChatInteractionOccurredEvent event) {
        if (!isValidPair(event.userId(), event.targetUserId())) {
            log.debug("Skip chat interaction feature update due to invalid pair: userId={}, targetUserId={}",
                    event.userId(), event.targetUserId());
            return;
        }

        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        upsertChatFeature(event.userId(), event.targetUserId(), occurredAt);
        upsertChatFeature(event.targetUserId(), event.userId(), occurredAt);
    }

    @Override
    @Transactional
    public void recordSocialFeedInteraction(SocialFeedInteractionOccurredEvent event) {
        if (!isValidPair(event.userId(), event.targetUserId()) || event.interactionType() == null) {
            log.debug("Skip social interaction feature update due to invalid event: userId={}, targetUserId={}, type={}",
                    event.userId(), event.targetUserId(), event.interactionType());
            return;
        }

        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        UserInteractionFeature feature = getOrCreate(event.userId(), event.targetUserId());

        incrementSocialCounter(feature, event.interactionType());
        feature.setLastSocialInteractionAt(maxInstant(feature.getLastSocialInteractionAt(), occurredAt));
        feature.setSocialFeedScore(calculateSocialFeedScore(feature, occurredAt));
        refreshRecentInteractionScore(feature);
        feature.setUpdatedAt(Instant.now());

        userInteractionFeatureRepository.save(feature);
    }

    private void upsertChatFeature(String userId, String targetUserId, Instant occurredAt) {
        UserInteractionFeature feature = getOrCreate(userId, targetUserId);

        feature.setMessageCount30d(feature.getMessageCount30d() + 1);
        feature.setLastMessageAt(maxInstant(feature.getLastMessageAt(), occurredAt));
        feature.setChatScore(calculateChatScore(feature, occurredAt));
        refreshRecentInteractionScore(feature);
        feature.setUpdatedAt(Instant.now());

        userInteractionFeatureRepository.save(feature);
    }

    private UserInteractionFeature getOrCreate(String userId, String targetUserId) {
        return userInteractionFeatureRepository.findByUserIdAndTargetUserId(userId, targetUserId)
                .orElseGet(() -> UserInteractionFeature.builder()
                        .id(UserInteractionFeature.idFor(userId, targetUserId))
                        .userId(userId)
                        .targetUserId(targetUserId)
                        .chatScore(0.0)
                        .socialFeedScore(0.0)
                        .recentInteractionScore(0.0)
                        .updatedAt(Instant.now())
                        .build());
    }

    private void incrementSocialCounter(UserInteractionFeature feature, InteractionType interactionType) {
        switch (interactionType) {
            case VIEW -> feature.setViewCount30d(feature.getViewCount30d() + 1);
            case LIKE -> feature.setReactionCount30d(feature.getReactionCount30d() + 1);
            case COMMENT -> feature.setCommentCount30d(feature.getCommentCount30d() + 1);
            case DISLIKE -> feature.setDislikeCount30d(feature.getDislikeCount30d() + 1);
            default -> {
            }
        }
    }

    private double calculateChatScore(UserInteractionFeature feature, Instant now) {
        double volumeScore = feature.getMessageCount30d() * CHAT_MESSAGE_WEIGHT;
        double recencyBoost = calculateRecencyBoost(feature.getLastMessageAt(), now);
        return cap(volumeScore + recencyBoost);
    }

    private double calculateSocialFeedScore(UserInteractionFeature feature, Instant now) {
        double score = feature.getViewCount30d() * VIEW_WEIGHT
                + feature.getReactionCount30d() * REACTION_WEIGHT
                + feature.getCommentCount30d() * COMMENT_WEIGHT
                - feature.getDislikeCount30d() * DISLIKE_WEIGHT
                + calculateRecencyBoost(feature.getLastSocialInteractionAt(), now);
        return cap(score);
    }

    private double calculateRecencyBoost(Instant lastInteractionAt, Instant now) {
        if (lastInteractionAt == null) {
            return 0.0;
        }

        long days = ChronoUnit.DAYS.between(lastInteractionAt, now);
        return Math.exp(-Math.max(days, 0) / RECENCY_DECAY_DAYS);
    }

    private void refreshRecentInteractionScore(UserInteractionFeature feature) {
        feature.setRecentInteractionScore(cap(Math.max(feature.getChatScore(), feature.getSocialFeedScore())));
    }

    private double cap(double score) {
        return Math.min(Math.max(score, 0.0), MAX_INTERACTION_SCORE);
    }

    private Instant maxInstant(Instant current, Instant candidate) {
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private boolean isValidPair(String userId, String targetUserId) {
        return StringUtils.hasText(userId)
                && StringUtils.hasText(targetUserId)
                && !userId.equals(targetUserId);
    }
}
