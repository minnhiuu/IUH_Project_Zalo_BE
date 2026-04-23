package com.bondhub.socialfeedservice.listener;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.event.socialfeed.PostCommentCountProjectionRequestedEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.UserSummaryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostCommentCountProjectionRequestedListener {

    CommentRepository commentRepository;
    PostRepository postRepository;
    UserSummaryRepository userSummaryRepository;
    RawNotificationEventPublisher rawNotificationEventPublisher;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.socialFeedEvents.postCommentCountProjectionRequested}",
            groupId = "social-feed-post-comment-count-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePostCommentCountProjectionRequested(
            @Payload PostCommentCountProjectionRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received post comment count projection event: topic={}, partition={}, offset={}, postId={}, commentId={}, action={}, actorId={}",
                topic, partition, offset, event.postId(), event.commentId(), event.action(), event.actorId());

        try {
            Post post = postRepository.findByIdAndActiveTrueAndIsCurrentTrue(event.postId())
                    .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

            publishCommentNotifications(post, event);

            long totalActiveComments = commentRepository.countByPostIdAndActiveTrue(event.postId());

            PostStats stats = post.getStats();
            if (stats == null) {
                stats = PostStats.builder().reactionCount(0).commentCount(0).shareCount(0).build();
            }
            stats.setCommentCount((int) totalActiveComments);
            post.setStats(stats);
            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process post comment count projection event: postId={}, commentId={}, action={}",
                    event.postId(), event.commentId(), event.action(), e);
            throw e;
        }
    }

    private void publishCommentNotifications(Post post, PostCommentCountProjectionRequestedEvent event) {
        if (!"INCREMENT".equalsIgnoreCase(event.action())) {
            return;
        }

        if (event.actorId() == null || event.actorId().isBlank()) {
            log.warn("Skipping comment notifications due to blank actorId: postId={}, commentId={}",
                    event.postId(), event.commentId());
            return;
        }

        Comment createdComment = commentRepository.findByIdAndActiveTrue(event.commentId()).orElse(null);
        if (createdComment == null) {
            log.warn("Skipping comment notification because comment is not active: commentId={}", event.commentId());
            return;
        }

        UserSummary actorSummary = userSummaryRepository.findById(event.actorId()).orElse(null);
        String actorName = actorSummary != null && actorSummary.getFullName() != null && !actorSummary.getFullName().isBlank()
                ? actorSummary.getFullName()
                : "Unknown User";
        String actorAvatar = actorSummary != null ? actorSummary.getAvatar() : null;

        publishPostAuthorNotification(post, createdComment, event.actorId(), actorName, actorAvatar);
        publishParentCommentAuthorNotification(createdComment, post.getId(), event.actorId(), actorName, actorAvatar);
    }

    private void publishPostAuthorNotification(
            Post post,
            Comment createdComment,
            String actorId,
            String actorName,
            String actorAvatar) {

        if (post.getAuthorId() == null || post.getAuthorId().equals(actorId)) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("postId", post.getId());
            payload.put("commentId", createdComment.getId());

            RawNotificationEvent notificationEvent = RawNotificationEvent.builder()
                    .recipientId(post.getAuthorId())
                    .actorId(actorId)
                    .actorName(actorName)
                    .actorAvatar(actorAvatar)
                    .type(NotificationType.POST_COMMENT)
                    .referenceId(post.getId())
                    .payload(payload)
                    .occurredAt(LocalDateTime.now())
                    .build();

            rawNotificationEventPublisher.publish(notificationEvent);
        } catch (Exception e) {
            log.warn("Failed to publish POST_COMMENT notification: postId={}, commentId={}",
                    post.getId(), createdComment.getId(), e);
        }
    }

    private void publishParentCommentAuthorNotification(
            Comment createdComment,
            String postId,
            String actorId,
            String actorName,
            String actorAvatar) {

        if (createdComment.getParentId() == null || createdComment.getParentId().isBlank()) {
            return;
        }

        Comment parentComment = commentRepository.findByIdAndActiveTrue(createdComment.getParentId()).orElse(null);
        if (parentComment == null || parentComment.getAuthorId() == null || parentComment.getAuthorId().equals(actorId)) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("postId", postId);
            payload.put("commentId", createdComment.getId());
            payload.put("parentCommentId", parentComment.getId());

            RawNotificationEvent notificationEvent = RawNotificationEvent.builder()
                    .recipientId(parentComment.getAuthorId())
                    .actorId(actorId)
                    .actorName(actorName)
                    .actorAvatar(actorAvatar)
                    .type(NotificationType.COMMENT_REPLY)
                    .referenceId(parentComment.getId())
                    .payload(payload)
                    .occurredAt(LocalDateTime.now())
                    .build();

            rawNotificationEventPublisher.publish(notificationEvent);
        } catch (Exception e) {
            log.warn("Failed to publish COMMENT_REPLY notification: parentCommentId={}, commentId={}",
                    createdComment.getParentId(), createdComment.getId(), e);
        }
    }
}