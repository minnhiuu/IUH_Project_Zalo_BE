package com.bondhub.socialfeedservice.listener;

import com.bondhub.common.event.socialfeed.UserInteractionEvent;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
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
public class PostViewRecordedListener {

    PostRepository postRepository;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.socialFeedEvents.postViewRecorded}",
            groupId = "social-feed-post-view-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePostViewRecorded(
            @Payload UserInteractionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received POST_VIEW_RECORDED event: topic={}, partition={}, offset={}, postId={}, userId={}",
                topic, partition, offset, event.postId(), event.userId());

        if (event.postId() == null || event.postId().isBlank()) {
            log.warn("Skipping view event with blank postId: topic={}, partition={}, offset={}", topic, partition, offset);
            acknowledgment.acknowledge();
            return;
        }

        postRepository.findByIdAndActiveTrueAndIsCurrentTrue(event.postId()).ifPresentOrElse(
                post -> {
                    incrementViewCount(post);
                    acknowledgment.acknowledge();
                },
                () -> {
                    log.warn("Post not found for view count increment, skipping: postId={}", event.postId());
                    acknowledgment.acknowledge();
                }
        );
    }

    private void incrementViewCount(Post post) {
        PostStats stats = post.getStats() == null ? PostStats.builder().build() : post.getStats();
        stats.setViewCount(stats.getViewCount() + 1);
        post.setStats(stats);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        log.debug("Incremented viewCount for postId={}, newCount={}", post.getId(), stats.getViewCount());
    }
}
