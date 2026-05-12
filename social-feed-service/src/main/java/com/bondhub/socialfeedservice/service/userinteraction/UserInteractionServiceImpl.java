package com.bondhub.socialfeedservice.service.userinteraction;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.socialfeedservice.RecentAuthorInteractionRequest;
import com.bondhub.common.dto.client.socialfeedservice.RecentAuthorInteractionResponse;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.response.interaction.UserInteractionResponse;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.UserInteraction;
import com.bondhub.socialfeedservice.publisher.PostDislikeEventPublisher;
import com.bondhub.socialfeedservice.publisher.PostViewEventPublisher;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.UserInteractionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInteractionServiceImpl implements UserInteractionService {

    UserInteractionRepository userInteractionRepository;
    PostRepository postRepository;
    PostViewEventPublisher postViewEventPublisher;
    PostDislikeEventPublisher postDislikeEventPublisher;
    SecurityUtil securityUtil;

    private static final int RECENT_INTERACTION_DAYS = 30;
    private static final int RECENT_INTERACTION_SCAN_LIMIT = 2_000;
    private static final double VIEW_WEIGHT = 0.2;
    private static final double REACTION_WEIGHT = 1.0;
    private static final double COMMENT_WEIGHT = 2.0;
    private static final double DISLIKE_WEIGHT = 2.0;
    private static final double SOCIAL_SCORE_CAP = 5.0;

    @Override
    public PageResponse<List<UserInteractionResponse>> getMyInteractions(int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<UserInteraction> interactions =
                userInteractionRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable);
        return PageResponse.fromPage(interactions, this::toResponse);
    }

    @Override
    public PageResponse<List<UserInteractionResponse>> getInteractionsByPost(String postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserInteraction> interactions =
                userInteractionRepository.findByPostIdOrderByCreatedAtDesc(postId, pageable);
        return PageResponse.fromPage(interactions, this::toResponse);
    }

    @Override
    public List<UserInteractionResponse> getNewestInteractionsByUser(String userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return userInteractionRepository.findTopByUserIdOrderByCreatedAtDesc(userId, pageable)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<RecentAuthorInteractionResponse> getRecentAuthorInteractions(RecentAuthorInteractionRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null || request == null || request.targetUserIds() == null || request.targetUserIds().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> targetUserIds = request.targetUserIds().stream()
                .filter(Objects::nonNull)
                .filter(userId -> !userId.isBlank())
                .filter(userId -> !userId.equals(currentUserId))
                .collect(Collectors.toSet());

        if (targetUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        Instant since = Instant.now().minus(RECENT_INTERACTION_DAYS, ChronoUnit.DAYS);
        List<UserInteraction> interactions = userInteractionRepository
                .findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        currentUserId,
                        since,
                        PageRequest.of(0, RECENT_INTERACTION_SCAN_LIMIT));

        if (interactions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Post> postById = postRepository.findAllByIdInAndActiveTrueAndIsCurrentTrue(
                        interactions.stream()
                                .map(UserInteraction::getPostId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(Post::getId, Function.identity(), (existing, replacement) -> existing));

        Map<String, SocialInteractionCounts> countsByAuthor = interactions.stream()
                .map(interaction -> toAuthorInteraction(interaction, postById.get(interaction.getPostId())))
                .filter(Objects::nonNull)
                .filter(authorInteraction -> targetUserIds.contains(authorInteraction.authorId()))
                .collect(Collectors.groupingBy(
                        AuthorInteraction::authorId,
                        Collectors.reducing(
                                SocialInteractionCounts.empty(),
                                authorInteraction -> SocialInteractionCounts.from(authorInteraction.interaction()),
                                SocialInteractionCounts::merge)));

        return countsByAuthor.entrySet().stream()
                .map(entry -> toRecentAuthorInteractionResponse(entry.getKey(), entry.getValue()))
                .filter(response -> response.socialInteractionScore() > 0.0)
                .toList();
    }

    @Override
    public void recordView(String postId) {
        String currentUserId = securityUtil.getCurrentUserId();

        boolean alreadyViewed = userInteractionRepository.existsByUserIdAndPostIdAndInteractionType(
                currentUserId, postId, InteractionType.VIEW);

        if (alreadyViewed) {
            log.debug("View already recorded, skipping: userId={}, postId={}", currentUserId, postId);
            return;
        }

        UserInteraction interaction = UserInteraction.builder()
                .userId(currentUserId)
                .postId(postId)
                .interactionType(InteractionType.VIEW)
                .weight(InteractionType.VIEW.getWeight())
                .createdAt(Instant.now())
                .build();

        userInteractionRepository.save(interaction);
        log.info("Recorded VIEW interaction: userId={}, postId={}", currentUserId, postId);

        // Publish async event so the listener increments PostStats.viewCount
        postViewEventPublisher.publishPostViewed(postId, currentUserId);
    }

    @Override
    public void recordDislike(String postId) {
        String currentUserId = securityUtil.getCurrentUserId();

        boolean alreadyDisliked = userInteractionRepository.existsByUserIdAndPostIdAndInteractionType(
                currentUserId, postId, InteractionType.DISLIKE);

        if (alreadyDisliked) {
            log.debug("Dislike already recorded, skipping: userId={}, postId={}", currentUserId, postId);
            return;
        }

        UserInteraction interaction = UserInteraction.builder()
                .userId(currentUserId)
                .postId(postId)
                .interactionType(InteractionType.DISLIKE)
                .weight(InteractionType.DISLIKE.getWeight())
                .createdAt(Instant.now())
                .build();

        userInteractionRepository.save(interaction);
        log.info("Recorded DISLIKE interaction: userId={}, postId={}", currentUserId, postId);

        postDislikeEventPublisher.publishPostDisliked(postId, currentUserId);
    }

    private UserInteractionResponse toResponse(UserInteraction interaction) {
        return UserInteractionResponse.builder()
                .id(interaction.getId())
                .userId(interaction.getUserId())
                .postId(interaction.getPostId())
                .groupId(interaction.getGroupId())
                .interactionType(interaction.getInteractionType())
                .weight(interaction.getWeight())
                .createdAt(interaction.getCreatedAt())
                .ingestedAt(interaction.getIngestedAt())
                .build();
    }

    private AuthorInteraction toAuthorInteraction(UserInteraction interaction, Post post) {
        if (interaction == null || post == null || post.getAuthorId() == null || interaction.getInteractionType() == null) {
            return null;
        }

        return new AuthorInteraction(post.getAuthorId(), interaction);
    }

    private RecentAuthorInteractionResponse toRecentAuthorInteractionResponse(String userId, SocialInteractionCounts counts) {
        return RecentAuthorInteractionResponse.builder()
                .userId(userId)
                .lastInteractionAt(counts.lastInteractionAt())
                .viewCount30d(counts.viewCount())
                .reactionCount30d(counts.reactionCount())
                .commentCount30d(counts.commentCount())
                .dislikeCount30d(counts.dislikeCount())
                .socialInteractionScore(calculateSocialInteractionScore(counts))
                .build();
    }

    private double calculateSocialInteractionScore(SocialInteractionCounts counts) {
        double score = counts.viewCount() * VIEW_WEIGHT
                + counts.reactionCount() * REACTION_WEIGHT
                + counts.commentCount() * COMMENT_WEIGHT
                - counts.dislikeCount() * DISLIKE_WEIGHT
                + calculateRecencyBoost(counts.lastInteractionAt());

        return Math.min(Math.max(score, 0.0), SOCIAL_SCORE_CAP);
    }

    private double calculateRecencyBoost(Instant lastInteractionAt) {
        if (lastInteractionAt == null) {
            return 0.0;
        }

        long days = ChronoUnit.DAYS.between(lastInteractionAt, Instant.now());
        if (days <= 1) {
            return 1.0;
        }
        if (days <= 7) {
            return 0.6;
        }
        if (days <= RECENT_INTERACTION_DAYS) {
            return 0.2;
        }
        return 0.0;
    }

    private record AuthorInteraction(String authorId, UserInteraction interaction) {
    }

    private record SocialInteractionCounts(
            int viewCount,
            int reactionCount,
            int commentCount,
            int dislikeCount,
            Instant lastInteractionAt
    ) {
        private static SocialInteractionCounts empty() {
            return new SocialInteractionCounts(0, 0, 0, 0, null);
        }

        private static SocialInteractionCounts from(UserInteraction interaction) {
            InteractionType type = interaction.getInteractionType();
            Instant createdAt = interaction.getCreatedAt();

            return new SocialInteractionCounts(
                    type == InteractionType.VIEW ? 1 : 0,
                    type == InteractionType.LIKE ? 1 : 0,
                    type == InteractionType.COMMENT ? 1 : 0,
                    type == InteractionType.DISLIKE ? 1 : 0,
                    createdAt);
        }

        private SocialInteractionCounts merge(SocialInteractionCounts other) {
            Instant latestInteractionAt = latest(lastInteractionAt, other.lastInteractionAt());
            return new SocialInteractionCounts(
                    viewCount + other.viewCount(),
                    reactionCount + other.reactionCount(),
                    commentCount + other.commentCount(),
                    dislikeCount + other.dislikeCount(),
                    latestInteractionAt);
        }

        private Instant latest(Instant first, Instant second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            return first.isAfter(second) ? first : second;
        }
    }
}
