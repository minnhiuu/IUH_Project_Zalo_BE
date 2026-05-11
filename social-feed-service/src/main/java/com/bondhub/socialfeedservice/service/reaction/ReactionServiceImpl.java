package com.bondhub.socialfeedservice.service.reaction;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.event.notification.payload.PostReactionPayload;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.request.reaction.ToggleReactionRequest;
import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.dto.response.reaction.ReactionResponse;
import com.bondhub.socialfeedservice.dto.response.reaction.ReactionStatsResponse;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.Reaction;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import com.bondhub.socialfeedservice.publisher.ReactionEventPublisher;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.ReactionRepository;
import com.bondhub.socialfeedservice.repository.UserSummaryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ReactionServiceImpl implements ReactionService {

    ReactionRepository reactionRepository;
    PostRepository postRepository;
    CommentRepository commentRepository;
    SecurityUtil securityUtil;
    ReactionEventPublisher reactionEventPublisher;
        UserSummaryRepository userSummaryRepository;
        RawNotificationEventPublisher rawNotificationEventPublisher;

    @Override
    @Transactional
    public ReactionResponse toggleReaction(ToggleReactionRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();

        validateTarget(request.targetId(), request.targetType());
        String postId = resolvePostId(request.targetId(), request.targetType());
        String groupId = resolveGroupId(postId);

        Reaction existingReaction = reactionRepository
                .findByAuthorIdAndTargetIdAndTargetType(currentUserId, request.targetId(), request.targetType())
                .orElse(null);

        boolean deactivate = existingReaction != null
                && existingReaction.isActive()
                && existingReaction.getType() == request.type();
        boolean desiredActive = !deactivate;

        Reaction reaction = existingReaction != null
                ? existingReaction
                : Reaction.builder()
                .authorId(currentUserId)
                .targetId(request.targetId())
                .targetType(request.targetType())
                .build();

        reaction.setType(request.type());
        reaction.setActive(desiredActive);

        Reaction savedReaction = reactionRepository.save(reaction);

        // Counter updates and notification fan-out are processed asynchronously via Kafka.
        reactionEventPublisher.publishProjectionRequested(
                currentUserId,
                request.targetId(),
                request.targetType(),
                request.type().name(),
                desiredActive);

        reactionEventPublisher.publishReactionInteraction(
                currentUserId,
                postId,
                request.type(),
                desiredActive,
                groupId);

        publishPostReactionNotification(currentUserId, request.targetId(), request.targetType(), request.type(), desiredActive);

        UserSummary author = userSummaryRepository.findById(currentUserId).orElse(null);
        return ReactionResponse.builder()
                .id(savedReaction.getId())
                .authorInfo(buildAuthorInfo(currentUserId, author))
                .targetId(request.targetId())
                .targetType(request.targetType())
                .type(request.type())
                .active(desiredActive)
                .totalReactions(getDenormalizedReactionCount(request.targetId(), request.targetType()))
                .build();
    }

    @Override
    @Transactional
    public ReactionResponse deleteReaction(String targetId, ReactionTargetType targetType) {
        String currentUserId = securityUtil.getCurrentUserId();

        validateTarget(targetId, targetType);

        Reaction existingReaction = reactionRepository
                .findByAuthorIdAndTargetIdAndTargetType(currentUserId, targetId, targetType)
                .orElse(null);

        if (existingReaction == null || !existingReaction.isActive()) {
            String authorId = existingReaction != null ? existingReaction.getAuthorId() : currentUserId;
            UserSummary author = userSummaryRepository.findById(authorId).orElse(null);
            return ReactionResponse.builder()
                    .id(existingReaction != null ? existingReaction.getId() : null)
                    .authorInfo(buildAuthorInfo(authorId, author))
                    .targetId(targetId)
                    .targetType(targetType)
                    .type(existingReaction != null ? existingReaction.getType() : null)
                    .active(false)
                    .totalReactions(getDenormalizedReactionCount(targetId, targetType))
                    .build();
        }

        existingReaction.setActive(false);
        Reaction savedReaction = reactionRepository.save(existingReaction);

        // Counter updates and notification fan-out are processed asynchronously via Kafka.
        reactionEventPublisher.publishProjectionRequested(
                currentUserId,
                targetId,
                targetType,
                existingReaction.getType().name(),
                false);

        UserSummary author = userSummaryRepository.findById(currentUserId).orElse(null);
        return ReactionResponse.builder()
                .id(savedReaction.getId())
                .authorInfo(buildAuthorInfo(currentUserId, author))
                .targetId(targetId)
                .targetType(targetType)
                .type(savedReaction.getType())
                .active(false)
                .totalReactions(getDenormalizedReactionCount(targetId, targetType))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReactionResponse> searchReactions(ReactionTargetType targetType, ReactionType reactionType) {
        List<Reaction> reactions = reactionRepository
                .findByTargetTypeAndTypeAndActiveTrueOrderByCreatedAtDesc(targetType, reactionType);

        Set<String> authorIds = reactions.stream().map(Reaction::getAuthorId).collect(Collectors.toSet());
        Map<String, UserSummary> authorById = userSummaryRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(UserSummary::getId, u -> u));

        long totalReactions = reactions.size();
        return reactions.stream()
                .map(reaction -> ReactionResponse.builder()
                        .id(reaction.getId())
                        .authorInfo(buildAuthorInfo(reaction.getAuthorId(), authorById.get(reaction.getAuthorId())))
                        .targetId(reaction.getTargetId())
                        .targetType(reaction.getTargetType())
                        .type(reaction.getType())
                        .active(reaction.isActive())
                        .totalReactions(totalReactions)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReactionStatsResponse getReactionStats(String targetId, ReactionTargetType targetType) {
        validateTarget(targetId, targetType);

        List<Reaction> reactions = reactionRepository
                .findByTargetIdAndTargetTypeAndActiveTrueOrderByCreatedAtDesc(targetId, targetType);

        Map<ReactionType, Long> countsByType = new EnumMap<>(ReactionType.class);
        for (ReactionType reactionType : ReactionType.values()) {
            countsByType.put(reactionType, 0L);
        }

        for (Reaction reaction : reactions) {
            ReactionType type = reaction.getType();
            countsByType.put(type, countsByType.getOrDefault(type, 0L) + 1);
        }

        return ReactionStatsResponse.builder()
                .targetId(targetId)
                .targetType(targetType)
                .totalReactions(reactions.size())
                .countsByType(countsByType)
                .build();
    }

    private AuthorInfo buildAuthorInfo(String authorId, UserSummary summary) {
        return AuthorInfo.builder()
                .id(authorId)
                .fullName(summary != null ? summary.getFullName() : null)
                .avatar(summary != null ? summary.getAvatar() : null)
                .build();
    }

    private void validateTarget(String targetId, ReactionTargetType targetType) {
        switch (targetType) {
            case POST -> postRepository.findByIdAndActiveTrueAndIsCurrentTrue(targetId)
                    .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
            case COMMENT -> commentRepository.findByIdAndActiveTrue(targetId)
                    .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
            default -> throw new AppException(ErrorCode.REACTION_NOT_FOUND);
        }
    }

    private String resolvePostId(String targetId, ReactionTargetType targetType) {
        if (targetType == ReactionTargetType.POST) {
            return targetId;
        }

        Comment targetComment = commentRepository.findByIdAndActiveTrue(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
        return targetComment.getPostId();
    }

    private String resolveGroupId(String postId) {
        return postRepository.findByIdAndActiveTrueAndIsCurrentTrue(postId)
                .map(Post::getGroupId)
                .orElse(null);
    }

    private long getDenormalizedReactionCount(String targetId, ReactionTargetType targetType) {
        if (targetType == ReactionTargetType.POST) {
            Post post = postRepository.findByIdAndActiveTrueAndIsCurrentTrue(targetId)
                    .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

            if (post.getStats() == null) {
                return 0;
            }

            return post.getStats().getReactionCount();
        }

        Comment comment = commentRepository.findByIdAndActiveTrue(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
        return comment.getReactionCount();
    }

        private void publishPostReactionNotification(
                        String actorId,
                        String targetId,
                        ReactionTargetType targetType,
                        ReactionType reactionType,
                        boolean desiredActive) {

                if (!desiredActive || targetType != ReactionTargetType.POST) {
                        log.debug("[ReactionNotification] Skip publish: targetType={}, desiredActive={}", targetType, desiredActive);
                        return;
                }

                Post post = postRepository.findByIdAndActiveTrueAndIsCurrentTrue(targetId).orElse(null);
                if (post == null || post.getAuthorId() == null || post.getAuthorId().equals(actorId)) {
                        log.debug("[ReactionNotification] Skip publish: post missing or self-reaction, targetId={}, actorId={}, postAuthorId={}",
                                        targetId,
                                        actorId,
                                        post != null ? post.getAuthorId() : null);
                        return;
                }

                UserSummary actorSummary = userSummaryRepository.findById(actorId).orElse(null);
                String actorName = actorSummary != null && actorSummary.getFullName() != null && !actorSummary.getFullName().isBlank()
                                ? actorSummary.getFullName()
                                : "Unknown User";
                String actorAvatar = actorSummary != null ? actorSummary.getAvatar() : null;

                try {
                        PostReactionPayload payload = PostReactionPayload.builder()
                                .postId(post.getId())
                                .reactionType(reactionType.name())
                                .build();

                        RawNotificationEvent notificationEvent = RawNotificationEvent.builder()
                                        .recipientId(post.getAuthorId())
                                        .actorId(actorId)
                                        .actorName(actorName)
                                        .actorAvatar(actorAvatar)
                                        .type(NotificationType.POST_LIKE)
                                        .referenceId(post.getId())
                                        .payload(payload)
                                        .occurredAt(LocalDateTime.now())
                                        .build();

                        rawNotificationEventPublisher.publish(notificationEvent);
                        log.info("[ReactionNotification] Published POST_LIKE: actorId={}, recipientId={}, postId={}, reactionType={}",
                                        actorId, post.getAuthorId(), post.getId(), reactionType);
                } catch (Exception e) {
                        log.warn("[ReactionNotification] Failed to publish POST_LIKE: actorId={}, targetId={}",
                                        actorId, targetId, e);
                }
        }

    @Override
    public void simulateBatchLikes(String postId, int count) {
        Post post = postRepository.findByIdAndActiveTrueAndIsCurrentTrue(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        org.springframework.data.domain.Page<UserSummary> usersPage = userSummaryRepository.findAll(org.springframework.data.domain.PageRequest.of(0, count));
        List<UserSummary> users = usersPage.getContent();

        for (int i = 0; i < count; i++) {
            String actorId;
            String actorName;
            String actorAvatar;

            if (i < users.size()) {
                UserSummary user = users.get(i);
                actorId = user.getId();
                actorName = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : "Batch User " + i;
                actorAvatar = user.getAvatar();
            } else {
                actorId = "dummy-batch-actor-" + i;
                actorName = "Batch User " + i;
                actorAvatar = null;
            }

            PostReactionPayload payload = PostReactionPayload.builder()
                    .postId(post.getId())
                    .reactionType(ReactionType.LIKE.name())
                    .build();

            RawNotificationEvent notificationEvent = RawNotificationEvent.builder()
                    .recipientId(post.getAuthorId())
                    .actorId(actorId)
                    .actorName(actorName)
                    .actorAvatar(actorAvatar)
                    .type(NotificationType.POST_LIKE)
                    .referenceId(post.getId())
                    .payload(payload)
                    .occurredAt(LocalDateTime.now())
                    .build();

            rawNotificationEventPublisher.publish(notificationEvent);
        }
        log.info("[ReactionNotification] Published {} simulated batch POST_LIKE events for postId={}", count, postId);
    }
}
