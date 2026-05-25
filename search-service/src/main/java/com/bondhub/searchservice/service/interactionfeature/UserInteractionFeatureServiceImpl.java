package com.bondhub.searchservice.service.interactionfeature;

import com.bondhub.common.dto.client.messageservice.ChatInteractionFeatureSnapshotResponse;
import com.bondhub.common.dto.client.socialfeedservice.SocialInteractionFeatureSnapshotResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    @Transactional
    public void upsertChatSnapshot(ChatInteractionFeatureSnapshotResponse snapshot) {
        if (snapshot == null || !isValidPair(snapshot.userId(), snapshot.targetUserId())) {
            return;
        }

        UserInteractionFeature feature = getOrCreate(snapshot.userId(), snapshot.targetUserId());
        feature.setMessageCount30d(Math.max(snapshot.messageCount30d(), 0));
        feature.setLastMessageAt(snapshot.lastMessageAt());
        feature.setChatScore(calculateChatScore(feature, Instant.now()));
        refreshRecentInteractionScore(feature);
        feature.setUpdatedAt(Instant.now());

        userInteractionFeatureRepository.save(feature);
    }

    @Override
    @Transactional
    public void upsertSocialSnapshot(SocialInteractionFeatureSnapshotResponse snapshot) {
        if (snapshot == null || !isValidPair(snapshot.userId(), snapshot.targetUserId())) {
            return;
        }

        UserInteractionFeature feature = getOrCreate(snapshot.userId(), snapshot.targetUserId());
        feature.setViewCount30d(Math.max(snapshot.viewCount30d(), 0));
        feature.setReactionCount30d(Math.max(snapshot.reactionCount30d(), 0));
        feature.setCommentCount30d(Math.max(snapshot.commentCount30d(), 0));
        feature.setDislikeCount30d(Math.max(snapshot.dislikeCount30d(), 0));
        feature.setLastSocialInteractionAt(snapshot.lastInteractionAt());
        feature.setSocialFeedScore(calculateSocialFeedScore(feature, Instant.now()));
        refreshRecentInteractionScore(feature);
        feature.setUpdatedAt(Instant.now());

        userInteractionFeatureRepository.save(feature);
    }

    @Override
    @Transactional
    public int upsertChatSnapshots(List<ChatInteractionFeatureSnapshotResponse> snapshots) {
        List<ChatInteractionFeatureSnapshotResponse> validSnapshots = snapshots == null
                ? Collections.emptyList()
                : snapshots.stream()
                .filter(snapshot -> snapshot != null && isValidPair(snapshot.userId(), snapshot.targetUserId()))
                .collect(Collectors.toMap(
                        snapshot -> UserInteractionFeature.idFor(snapshot.userId(), snapshot.targetUserId()),
                        Function.identity(),
                        this::mergeChatSnapshot))
                .values()
                .stream()
                .toList();
        if (validSnapshots.isEmpty()) {
            return 0;
        }

        Map<String, UserInteractionFeature> featureById = findExistingFeatures(validSnapshots.stream()
                .map(snapshot -> UserInteractionFeature.idFor(snapshot.userId(), snapshot.targetUserId()))
                .toList());
        Instant now = Instant.now();
        List<UserInteractionFeature> features = validSnapshots.stream()
                .map(snapshot -> applyChatSnapshot(
                        featureById.getOrDefault(
                                UserInteractionFeature.idFor(snapshot.userId(), snapshot.targetUserId()),
                                createEmptyFeature(snapshot.userId(), snapshot.targetUserId())),
                        snapshot,
                        now))
                .toList();

        userInteractionFeatureRepository.saveAll(features);
        return features.size();
    }

    @Override
    @Transactional
    public int upsertSocialSnapshots(List<SocialInteractionFeatureSnapshotResponse> snapshots) {
        List<SocialInteractionFeatureSnapshotResponse> validSnapshots = snapshots == null
                ? Collections.emptyList()
                : snapshots.stream()
                .filter(snapshot -> snapshot != null && isValidPair(snapshot.userId(), snapshot.targetUserId()))
                .collect(Collectors.toMap(
                        snapshot -> UserInteractionFeature.idFor(snapshot.userId(), snapshot.targetUserId()),
                        Function.identity(),
                        this::mergeSocialSnapshot))
                .values()
                .stream()
                .toList();
        if (validSnapshots.isEmpty()) {
            return 0;
        }

        Map<String, UserInteractionFeature> featureById = findExistingFeatures(validSnapshots.stream()
                .map(snapshot -> UserInteractionFeature.idFor(snapshot.userId(), snapshot.targetUserId()))
                .toList());
        Instant now = Instant.now();
        List<UserInteractionFeature> features = validSnapshots.stream()
                .map(snapshot -> applySocialSnapshot(
                        featureById.getOrDefault(
                                UserInteractionFeature.idFor(snapshot.userId(), snapshot.targetUserId()),
                                createEmptyFeature(snapshot.userId(), snapshot.targetUserId())),
                        snapshot,
                        now))
                .toList();

        userInteractionFeatureRepository.saveAll(features);
        return features.size();
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
                .orElseGet(() -> createEmptyFeature(userId, targetUserId));
    }

    private Map<String, UserInteractionFeature> findExistingFeatures(List<String> ids) {
        return userInteractionFeatureRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserInteractionFeature::getId, Function.identity()));
    }

    private UserInteractionFeature createEmptyFeature(String userId, String targetUserId) {
        return UserInteractionFeature.builder()
                .id(UserInteractionFeature.idFor(userId, targetUserId))
                .userId(userId)
                .targetUserId(targetUserId)
                .chatScore(0.0)
                .socialFeedScore(0.0)
                .recentInteractionScore(0.0)
                .updatedAt(Instant.now())
                .build();
    }

    private UserInteractionFeature applyChatSnapshot(
            UserInteractionFeature feature,
            ChatInteractionFeatureSnapshotResponse snapshot,
            Instant now) {
        feature.setMessageCount30d(Math.max(snapshot.messageCount30d(), 0));
        feature.setLastMessageAt(snapshot.lastMessageAt());
        feature.setChatScore(calculateChatScore(feature, now));
        refreshRecentInteractionScore(feature);
        feature.setUpdatedAt(now);
        return feature;
    }

    private UserInteractionFeature applySocialSnapshot(
            UserInteractionFeature feature,
            SocialInteractionFeatureSnapshotResponse snapshot,
            Instant now) {
        feature.setViewCount30d(Math.max(snapshot.viewCount30d(), 0));
        feature.setReactionCount30d(Math.max(snapshot.reactionCount30d(), 0));
        feature.setCommentCount30d(Math.max(snapshot.commentCount30d(), 0));
        feature.setDislikeCount30d(Math.max(snapshot.dislikeCount30d(), 0));
        feature.setLastSocialInteractionAt(snapshot.lastInteractionAt());
        feature.setSocialFeedScore(calculateSocialFeedScore(feature, now));
        refreshRecentInteractionScore(feature);
        feature.setUpdatedAt(now);
        return feature;
    }

    private ChatInteractionFeatureSnapshotResponse mergeChatSnapshot(
            ChatInteractionFeatureSnapshotResponse first,
            ChatInteractionFeatureSnapshotResponse second) {
        return ChatInteractionFeatureSnapshotResponse.builder()
                .userId(first.userId())
                .targetUserId(first.targetUserId())
                .messageCount30d(first.messageCount30d() + second.messageCount30d())
                .lastMessageAt(maxInstant(first.lastMessageAt(), second.lastMessageAt()))
                .build();
    }

    private SocialInteractionFeatureSnapshotResponse mergeSocialSnapshot(
            SocialInteractionFeatureSnapshotResponse first,
            SocialInteractionFeatureSnapshotResponse second) {
        return SocialInteractionFeatureSnapshotResponse.builder()
                .userId(first.userId())
                .targetUserId(first.targetUserId())
                .viewCount30d(first.viewCount30d() + second.viewCount30d())
                .reactionCount30d(first.reactionCount30d() + second.reactionCount30d())
                .commentCount30d(first.commentCount30d() + second.commentCount30d())
                .dislikeCount30d(first.dislikeCount30d() + second.dislikeCount30d())
                .lastInteractionAt(maxInstant(first.lastInteractionAt(), second.lastInteractionAt()))
                .build();
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
