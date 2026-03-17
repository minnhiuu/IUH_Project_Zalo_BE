package com.leafy.socialfeedservice.service.reaction;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.leafy.socialfeedservice.dto.request.reaction.ToggleReactionRequest;
import com.leafy.socialfeedservice.dto.response.reaction.ReactionResponse;
import com.leafy.socialfeedservice.model.Comment;
import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.model.Reaction;
import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import com.leafy.socialfeedservice.publisher.ReactionEventPublisher;
import com.leafy.socialfeedservice.repository.CommentRepository;
import com.leafy.socialfeedservice.repository.PostRepository;
import com.leafy.socialfeedservice.repository.ReactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReactionServiceImpl implements ReactionService {

    ReactionRepository reactionRepository;
    PostRepository postRepository;
    CommentRepository commentRepository;
    SecurityUtil securityUtil;
    ReactionEventPublisher reactionEventPublisher;

    @Override
    @Transactional
    public ReactionResponse toggleReaction(ToggleReactionRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();

        validateTarget(request.targetId(), request.targetType());

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

        return ReactionResponse.builder()
                .id(savedReaction.getId())
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
            return ReactionResponse.builder()
                    .id(existingReaction != null ? existingReaction.getId() : null)
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

        return ReactionResponse.builder()
                .id(savedReaction.getId())
                .targetId(targetId)
                .targetType(targetType)
                .type(savedReaction.getType())
                .active(false)
                .totalReactions(getDenormalizedReactionCount(targetId, targetType))
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
}
