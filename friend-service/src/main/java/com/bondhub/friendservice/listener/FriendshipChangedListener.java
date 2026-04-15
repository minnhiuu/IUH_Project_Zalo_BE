package com.bondhub.friendservice.listener;

import com.bondhub.common.enums.FriendshipAction;
import com.bondhub.common.event.friend.FriendshipChangedEvent;
import com.bondhub.friendservice.graph.service.GraphFriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipChangedListener {

    private final GraphFriendService graphFriendService;

    @KafkaListener(
            topics = "${kafka.topics.friendship-changed:friend.friendship.changed}",
            groupId = "friend-service-graph-sync",
            containerFactory = "friendshipChangedListenerFactory"
    )
    public void handleFriendshipChanged(
            @Payload FriendshipChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("📥 Received FriendshipChangedEvent: topic={}, partition={}, offset={}, action={}, userA={}, userB={}",
                topic, partition, offset, event.action(), event.userA(), event.userB());

        try {
            switch (event.action()) {
                case ADDED -> {
                    graphFriendService.createFriendRelationship(event.userA(), event.userB());
                    log.info("✅ Created FRIEND relationship in Neo4j: {} <-> {}", event.userA(), event.userB());
                }
                case REMOVED -> {
                    graphFriendService.removeFriendRelationship(event.userA(), event.userB());
                    log.info("✅ Removed FRIEND relationship from Neo4j: {} <-> {}", event.userA(), event.userB());
                }
                default -> log.debug("Ignoring FriendshipChangedEvent action: {}", event.action());
            }

            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.debug("✅ Message acknowledged: offset={}", offset);
            }

        } catch (Exception e) {
            log.error("❌ Failed to process FriendshipChangedEvent: action={}, userA={}, userB={}, error={}",
                    event.action(), event.userA(), event.userB(), e.getMessage(), e);
            // Don't acknowledge — will be retried
        }
    }
}
