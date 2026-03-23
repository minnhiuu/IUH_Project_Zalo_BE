package com.leafy.socialfeedservice.listener;

import com.bondhub.common.event.socialfeed.ReactionToggleCommandEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.leafy.socialfeedservice.model.Comment;
import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.model.Reaction;
import com.leafy.socialfeedservice.model.embedded.PostStats;
import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import com.leafy.socialfeedservice.model.enums.ReactionType;
import com.leafy.socialfeedservice.repository.CommentRepository;
import com.leafy.socialfeedservice.repository.PostRepository;
import com.leafy.socialfeedservice.repository.ReactionRepository;
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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReactionToggleCommandRequestedListener {

    ReactionRepository reactionRepository;
    PostRepository postRepository;
    CommentRepository commentRepository;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.socialFeedEvents.reactionToggleCommandRequested}",
            groupId = "social-feed-reaction-command-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleToggleCommandRequested(
            @Payload ReactionToggleCommandEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received reaction projection event: topic={}, partition={}, offset={}, authorId={}, targetId={}, targetType={}, reactionType={}, desiredActive={}",
                topic, partition, offset, event.authorId(), event.targetId(), event.targetType(), event.reactionType(), event.desiredActive());

        try {
            ReactionTargetType targetType = ReactionTargetType.valueOf(event.targetType());

                        List<Reaction> activeReactions = reactionRepository.findByTargetIdAndTargetTypeAndActiveTrueOrderByCreatedAtDesc(
                    event.targetId(),
                    targetType);
                        long totalReactions = activeReactions.size();

            if (targetType == ReactionTargetType.POST) {
                Post post = postRepository.findByIdAndActiveTrueAndIsCurrentTrue(event.targetId())
                        .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

                PostStats stats = post.getStats();
                if (stats == null) {
                    stats = PostStats.builder().reactionCount(0).commentCount(0).shareCount(0).build();
                }

                                Map<ReactionType, Long> reactionCounts = new EnumMap<>(ReactionType.class);
                                for (Reaction reaction : activeReactions) {
                                        ReactionType reactionType = reaction.getType();
                                        reactionCounts.put(reactionType, reactionCounts.getOrDefault(reactionType, 0L) + 1);
                                }

                                List<ReactionType> topReactions = reactionCounts.entrySet().stream()
                                                .sorted(Comparator
                                                                .comparing(Map.Entry<ReactionType, Long>::getValue, Comparator.reverseOrder())
                                                                .thenComparing(entry -> entry.getKey().name()))
                                                .limit(3)
                                                .map(Map.Entry::getKey)
                                                .toList();

                stats.setReactionCount((int) totalReactions);
                                stats.setTopReactions(topReactions);
                post.setStats(stats);
                post.setUpdatedAt(LocalDateTime.now());
                postRepository.save(post);
            } else {
                Comment comment = commentRepository.findByIdAndActiveTrue(event.targetId())
                        .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

                comment.setReactionCount((int) totalReactions);
                comment.setLastModifiedAt(LocalDateTime.now());
                commentRepository.save(comment);
            }

            // Notification dispatch hook - downstream service can subscribe to this topic and notify target owners.
            log.info("Reaction notification hook: actorId={}, targetId={}, targetType={}, reactionType={}, active={}",
                    event.authorId(), event.targetId(), event.targetType(), event.reactionType(), event.desiredActive());

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process reaction projection event: targetId={}, targetType={}",
                    event.targetId(), event.targetType(), e);
            throw e;
        }
    }
}