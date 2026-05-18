package com.bondhub.socialfeedservice.publisher;

import com.bondhub.common.event.search.SocialFeedInteractionOccurredEvent;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.repository.PostRepository;
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
public class SocialFeedInteractionEventPublisher {

    OutboxEventPublisher outboxEventPublisher;
    PostRepository postRepository;

    @Transactional
    public void publishPostAuthorInteraction(String userId, String postId, InteractionType interactionType) {
        if (userId == null || postId == null || interactionType == null) {
            return;
        }

        Post post = postRepository.findByIdAndActiveTrueAndIsCurrentTrueAndHiddenFalse(postId).orElse(null);
        if (post == null || post.getAuthorId() == null || post.getAuthorId().equals(userId)) {
            return;
        }

        SocialFeedInteractionOccurredEvent event = SocialFeedInteractionOccurredEvent.builder()
                .userId(userId)
                .targetUserId(post.getAuthorId())
                .postId(postId)
                .interactionType(interactionType)
                .occurredAt(Instant.now())
                .build();

        outboxEventPublisher.saveAndPublish(
                postId,
                "Post",
                EventType.SOCIAL_FEED_INTERACTION_OCCURRED,
                event
        );

        log.debug("Published SOCIAL_FEED_INTERACTION_OCCURRED: userId={}, targetUserId={}, postId={}, interactionType={}",
                userId, post.getAuthorId(), postId, interactionType);
    }
}
