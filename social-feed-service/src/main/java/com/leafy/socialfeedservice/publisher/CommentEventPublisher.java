package com.leafy.socialfeedservice.publisher;

import com.bondhub.common.event.socialfeed.PostCommentCountProjectionRequestedEvent;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.common.event.socialfeed.UserInteractionEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
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
public class CommentEventPublisher {

    OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publishPostCommentCountProjectionRequested(
            String actorId,
            String postId,
            String commentId,
            String action) {

        PostCommentCountProjectionRequestedEvent event = PostCommentCountProjectionRequestedEvent.builder()
                .actorId(actorId)
                .postId(postId)
                .commentId(commentId)
                .action(action)
                .timestamp(System.currentTimeMillis())
                .build();

        outboxEventPublisher.saveAndPublish(
                postId,
                "Post",
                EventType.POST_COMMENT_COUNT_PROJECTION_REQUESTED,
                event
        );

        log.info("Published POST_COMMENT_COUNT_PROJECTION_REQUESTED: actorId={}, postId={}, commentId={}, action={}",
                actorId, postId, commentId, action);
    }

    @Transactional
    public void publishCommentInteraction(
            String userId,
            String postId,
            String groupId) {

        UserInteractionEvent event = UserInteractionEvent.builder()
                .userId(userId)
                .postId(postId)
                .interactionType(InteractionType.COMMENT)
                .weight(InteractionType.COMMENT.getWeight())
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
                userId, postId, event.interactionType(), event.weight(), groupId);
    }
}
