package com.leafy.socialfeedservice.publisher;

import com.bondhub.common.event.socialfeed.ReactionToggleCommandEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
