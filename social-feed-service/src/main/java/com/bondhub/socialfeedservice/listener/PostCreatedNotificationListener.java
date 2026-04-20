package com.bondhub.socialfeedservice.listener;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.event.socialfeed.PostEvent;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.socialfeedservice.client.FriendServiceClient;
import com.bondhub.socialfeedservice.model.UserSummary;
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
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostCreatedNotificationListener {

    FriendServiceClient friendServiceClient;
    UserSummaryRepository userSummaryRepository;
    RawNotificationEventPublisher rawNotificationEventPublisher;

    @KafkaListener(
            topics = "#{kafkaTopicProperties.socialFeedEvents.postCreated}",
            groupId = "social-feed-post-created-notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePostCreated(
            @Payload PostEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received post created event for notification fan-out: topic={}, partition={}, offset={}, postId={}, authorId={}",
                topic, partition, offset, event.postId(), event.authorId());

        try {
            if (isBlank(event.postId()) || isBlank(event.authorId())) {
                log.warn("Skipping post notification fan-out due to missing postId/authorId: postId={}, authorId={}",
                        event.postId(), event.authorId());
                acknowledgment.acknowledge();
                return;
            }

            Set<String> friendIds = fetchFriendIds(event.authorId());
            if (friendIds.isEmpty()) {
                acknowledgment.acknowledge();
                return;
            }

            UserSummary actorSummary = userSummaryRepository.findById(event.authorId()).orElse(null);
            String actorName = actorSummary != null && !isBlank(actorSummary.getFullName())
                    ? actorSummary.getFullName()
                    : "Unknown User";
            String actorAvatar = actorSummary != null ? actorSummary.getAvatar() : null;

            int published = 0;
            for (String recipientId : friendIds) {
                if (isBlank(recipientId) || recipientId.equals(event.authorId())) {
                    continue;
                }

                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("postId", event.postId());
                    payload.put("groupId", event.groupId());
                    payload.put("postType", event.postType());
                    payload.put("visibility", event.visibility());

                    RawNotificationEvent notificationEvent = RawNotificationEvent.builder()
                            .recipientId(recipientId)
                            .actorId(event.authorId())
                            .actorName(actorName)
                            .actorAvatar(actorAvatar)
                            .type(NotificationType.POST_PUBLISHED)
                            .referenceId(event.postId())
                            .payload(payload)
                            .occurredAt(LocalDateTime.now())
                            .build();

                    rawNotificationEventPublisher.publish(notificationEvent);
                    published++;
                } catch (Exception publishEx) {
                    log.warn("Failed to publish POST_PUBLISHED for postId={}, recipientId={}",
                            event.postId(), recipientId, publishEx);
                }
            }

            log.info("Completed post notification fan-out: postId={}, authorId={}, recipients={}, published={}",
                    event.postId(), event.authorId(), friendIds.size(), published);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process post created event for notifications: postId={}, authorId={}",
                    event.postId(), event.authorId(), e);
            throw e;
        }
    }

    private Set<String> fetchFriendIds(String authorId) {
        try {
            var response = friendServiceClient.getFriendIdsInternal(authorId);
            if (response == null || response.data() == null) {
                return Set.of();
            }
            return response.data();
        } catch (Exception e) {
            log.warn("Failed to fetch friend IDs for authorId={} while fan-out post notifications", authorId, e);
            return Set.of();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
