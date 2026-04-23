package com.bondhub.socialfeedservice.publisher;

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
public class PostViewEventPublisher {

    OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publishPostViewed(String postId, String userId) {
        UserInteractionEvent event = UserInteractionEvent.builder()
                .postId(postId)
                .userId(userId)
                .interactionType(InteractionType.VIEW)
                .weight(InteractionType.VIEW.getWeight())
                .createdAt(Instant.now())
                .build();

        outboxEventPublisher.saveAndPublish(
                postId,
                "Post",
                EventType.POST_VIEW_RECORDED,
                event
        );

        log.debug("Published POST_VIEW_RECORDED event: postId={}, userId={}", postId, userId);
    }
}
