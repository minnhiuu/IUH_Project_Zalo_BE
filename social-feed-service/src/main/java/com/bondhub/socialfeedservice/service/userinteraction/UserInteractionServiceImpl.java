package com.bondhub.socialfeedservice.service.userinteraction;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.socialfeedservice.SocialInteractionFeatureSnapshotResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.dto.response.interaction.ViewerResponse;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.repository.UserSummaryRepository;
import java.util.Objects;
import java.util.function.Function;

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
    UserSummaryRepository userSummaryRepository;

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
    public PageResponse<List<ViewerResponse>> getViewersByPost(String postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserInteraction> interactions =
                userInteractionRepository.findByPostIdAndInteractionTypeOrderByCreatedAtDesc(postId, InteractionType.VIEW, pageable);

        Set<String> userIds = interactions.getContent().stream().map(UserInteraction::getUserId).collect(Collectors.toSet());
        Map<String, UserSummary> summaryById = userSummaryRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserSummary::getId, u -> u));

        List<ViewerResponse> viewers = interactions.getContent().stream().map(interaction -> {
            UserSummary summary = summaryById.get(interaction.getUserId());
            return ViewerResponse.builder()
                    .id(interaction.getId())
                    .authorInfo(AuthorInfo.builder()
                            .id(interaction.getUserId())
                            .fullName(summary != null ? summary.getFullName() : null)
                            .avatar(summary != null ? summary.getAvatar() : null)
                            .build())
                    .viewedAt(interaction.getCreatedAt())
                    .build();
        }).toList();

        Page<ViewerResponse> viewerPage = new PageImpl<>(viewers, pageable, interactions.getTotalElements());
        return PageResponse.fromPage(viewerPage, v -> v);
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
    public List<SocialInteractionFeatureSnapshotResponse> getSearchInteractionFeatureSnapshot(int sinceDays, int limit) {
        Instant since = Instant.now().minus(Math.max(sinceDays, 1), ChronoUnit.DAYS);
        int boundedLimit = Math.min(Math.max(limit, 1), 10_000);
        List<UserInteraction> interactions = userInteractionRepository
                .findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since, PageRequest.of(0, boundedLimit));
        if (interactions.isEmpty()) {
            return List.of();
        }

        Map<String, Post> postById = postRepository.findAllByIdInAndActiveTrueAndIsCurrentTrue(
                        interactions.stream()
                                .map(UserInteraction::getPostId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(Post::getId, Function.identity(), (existing, replacement) -> existing));

        return interactions.stream()
                .map(interaction -> toAuthorInteraction(interaction, postById.get(interaction.getPostId())))
                .filter(Objects::nonNull)
                .filter(authorInteraction -> !authorInteraction.interaction().getUserId().equals(authorInteraction.authorId()))
                .collect(Collectors.groupingBy(
                        authorInteraction -> new SocialFeatureKey(
                                authorInteraction.interaction().getUserId(),
                                authorInteraction.authorId()),
                        Collectors.reducing(
                                SocialFeatureCounts.empty(),
                                authorInteraction -> SocialFeatureCounts.from(authorInteraction.interaction()),
                                SocialFeatureCounts::merge)))
                .entrySet()
                .stream()
                .map(entry -> toSnapshotResponse(entry.getKey(), entry.getValue()))
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
        if (interaction == null
                || post == null
                || interaction.getUserId() == null
                || post.getAuthorId() == null
                || interaction.getInteractionType() == null) {
            return null;
        }

        return new AuthorInteraction(post.getAuthorId(), interaction);
    }

    private SocialInteractionFeatureSnapshotResponse toSnapshotResponse(
            SocialFeatureKey key,
            SocialFeatureCounts counts) {
        return SocialInteractionFeatureSnapshotResponse.builder()
                .userId(key.userId())
                .targetUserId(key.targetUserId())
                .lastInteractionAt(counts.lastInteractionAt())
                .viewCount30d(counts.viewCount())
                .reactionCount30d(counts.reactionCount())
                .commentCount30d(counts.commentCount())
                .dislikeCount30d(counts.dislikeCount())
                .build();
    }

    private record AuthorInteraction(String authorId, UserInteraction interaction) {
    }

    private record SocialFeatureKey(String userId, String targetUserId) {
    }

    private record SocialFeatureCounts(
            int viewCount,
            int reactionCount,
            int commentCount,
            int dislikeCount,
            Instant lastInteractionAt
    ) {
        private static SocialFeatureCounts empty() {
            return new SocialFeatureCounts(0, 0, 0, 0, null);
        }

        private static SocialFeatureCounts from(UserInteraction interaction) {
            InteractionType type = interaction.getInteractionType();
            return new SocialFeatureCounts(
                    type == InteractionType.VIEW ? 1 : 0,
                    type == InteractionType.LIKE ? 1 : 0,
                    type == InteractionType.COMMENT ? 1 : 0,
                    type == InteractionType.DISLIKE ? 1 : 0,
                    interaction.getCreatedAt());
        }

        private SocialFeatureCounts merge(SocialFeatureCounts other) {
            return new SocialFeatureCounts(
                    viewCount + other.viewCount(),
                    reactionCount + other.reactionCount(),
                    commentCount + other.commentCount(),
                    dislikeCount + other.dislikeCount(),
                    latest(lastInteractionAt, other.lastInteractionAt()));
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
