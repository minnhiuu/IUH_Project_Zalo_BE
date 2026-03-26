package com.bondhub.socialfeedservice.publisher;

import com.bondhub.common.event.socialfeed.PostEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.socialfeedservice.model.Post;
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
        var content = post.getContent() != null ? post.getContent() : post.getSharedCaption();

        PostEvent event = PostEvent.builder()
                .postId(post.getId())
                .authorId(post.getAuthorId())
                .groupId(post.getGroupId())
            .caption(content != null ? content.getCaption() : null)
            .hashtags(content != null ? content.getHashtags() : null)
            .postType(post.getPostType() != null ? post.getPostType().name() : null)
            .sharedPostId(post.getSharedPostId())
            .originalAuthorId(post.getOriginalAuthorId())
            .rootPostId(post.getRootPostId())
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
