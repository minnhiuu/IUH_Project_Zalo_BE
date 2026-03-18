package com.leafy.socialfeedservice.publisher;

import com.bondhub.common.event.socialfeed.ReactionToggleCommandEvent;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.common.event.socialfeed.UserInteractionEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import com.leafy.socialfeedservice.model.enums.ReactionType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReactionEventPublisher {

    OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publishProjectionRequested(
            String authorId,
            String targetId,
            ReactionTargetType targetType,
            String reactionType,
            boolean desiredActive) {

        ReactionToggleCommandEvent event = ReactionToggleCommandEvent.builder()
                .authorId(authorId)
                .targetId(targetId)
                .targetType(targetType.name())
                .reactionType(reactionType)
                .desiredActive(desiredActive)
                .timestamp(System.currentTimeMillis())
                .build();

        outboxEventPublisher.saveAndPublish(
                targetId,
                "Reaction",
                EventType.REACTION_TOGGLE_COMMAND_REQUESTED,
                event
        );

        log.info("Published REACTION_TOGGLE_COMMAND_REQUESTED for async projection: authorId={}, targetId={}, targetType={}, reactionType={}, desiredActive={}",
                authorId, targetId, targetType, reactionType, desiredActive);
    }

    @Transactional
    public void publishReactionInteraction(
            String userId,
            String postId,
            ReactionType reactionType,
            boolean active,
            String groupId) {

        if (!active) {
            return;
        }

        InteractionType interactionType = mapReactionToInteractionType(reactionType);

        UserInteractionEvent event = UserInteractionEvent.builder()
                .userId(userId)
                .postId(postId)
                .interactionType(interactionType)
                .weight(interactionType.getWeight())
                .createdAt(Instant.now())
                .groupId(groupId)
                .build();

        outboxEventPublisher.saveAndPublish(
                postId,
                "Post",
                EventType.USER_INTERACTION_RECORDED,
                event
        );

        log.info("Published USER_INTERACTION_RECORDED: userId={}, postId={}, interactionType={}, weight={}, groupId={}",
                userId, postId, interactionType, event.weight(), groupId);
    }

    private InteractionType mapReactionToInteractionType(ReactionType reactionType) {
        return switch (reactionType) {
            case SAD, ANGRY -> InteractionType.DISLIKE;
            default -> InteractionType.LIKE;
        };
    }
}
