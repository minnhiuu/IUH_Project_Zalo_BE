package com.bondhub.socialfeedservice.listener;

import com.bondhub.common.event.socialfeed.PostCommentCountProjectionRequestedEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostCommentCountProjectionRequestedListener {

    CommentRepository commentRepository;
    PostRepository postRepository;

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
}