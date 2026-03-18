package com.leafy.socialfeedservice.publisher;

import com.bondhub.common.event.socialfeed.PostEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.leafy.socialfeedservice.model.Post;
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
public class PostEventPublisher {

    OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publishPostCreated(Post post) {
        publishPostEvent(post, EventType.POST_CREATED, "POST_CREATED");
    }

    @Transactional
    public void publishPostUpdated(Post post) {
        publishPostEvent(post, EventType.POST_UPDATED, "POST_UPDATED");
    }

    @Transactional
    public void publishPostDeleted(Post post) {
        publishPostEvent(post, EventType.POST_DELETED, "POST_DELETED");
    }

    private void publishPostEvent(Post post, EventType eventType, String logEventType) {
        PostEvent event = PostEvent.builder()
                .postId(post.getId())
                .authorId(post.getAuthorId())
                .groupId(post.getGroupId())
                .title(post.getContent() != null ? post.getContent().getTitle() : null)
                .caption(post.getContent() != null ? post.getContent().getCaption() : null)
                .description(post.getContent() != null ? post.getContent().getDescription() : null)
                .visibility(post.getVisibility() != null ? post.getVisibility().name() : null)
                .uploadedAt(post.getUploadedAt())
                .updatedAt(post.getUpdatedAt())
                .build();

        outboxEventPublisher.saveAndPublish(
                post.getId(),
                "Post",
                eventType,
                event
        );

        log.info("Published {} event: postId={}, authorId={}", logEventType, post.getId(), post.getAuthorId());
    }
}
