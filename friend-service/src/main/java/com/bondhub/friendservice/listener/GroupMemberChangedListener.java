package com.bondhub.friendservice.listener;

import com.bondhub.common.event.group.GroupMemberChangedEvent;
import com.bondhub.friendservice.graph.repository.UserNodeRepository;
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
public class GroupMemberChangedListener {

    private final UserNodeRepository userNodeRepository;

    @KafkaListener(
            topics = "${kafka.topics.group-member-changed:group.member.changed}",
            groupId = "friend-service-graph-sync",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleGroupMemberChanged(
            @Payload GroupMemberChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received GroupMemberChangedEvent: action={}, groupId={}, userId={}",
                event.action(), event.groupId(), event.userId());

        try {
            switch (event.action()) {
                case JOINED -> {
                    userNodeRepository.mergeInGroupRelationship(event.userId(), event.groupId());
                    log.info("Created IN_GROUP relationship in Neo4j: {} -> {}", event.userId(), event.groupId());
                }
                case LEFT -> {
                    userNodeRepository.removeInGroupRelationship(event.userId(), event.groupId());
                    log.info("Removed IN_GROUP relationship from Neo4j: {} -> {}", event.userId(), event.groupId());
                }
            }

            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            log.error("Failed to process GroupMemberChangedEvent: groupId={}, userId={}, error={}",
                    event.groupId(), event.userId(), e.getMessage(), e);
        }
    }
}
