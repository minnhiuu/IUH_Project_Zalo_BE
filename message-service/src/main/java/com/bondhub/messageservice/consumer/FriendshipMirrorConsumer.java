package com.bondhub.messageservice.consumer;

import com.bondhub.common.enums.FriendshipAction;
import com.bondhub.common.event.friend.FriendshipChangedEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.dto.SocketEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.bondhub.messageservice.dto.response.ConversationResponse;
import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipMirrorConsumer {

    private final MongoTemplate mongoTemplate;
    private final ConversationService conversationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    @KafkaListener(topics = "${kafka.topics.friend-events.friendship-changed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleFriendshipChanged(FriendshipChangedEvent event) {
        log.info("Received FriendshipChangedEvent: userA={}, userB={}, action={}", event.userA(), event.userB(), event.action());

        updateFriendList(event.userA(), event.userB(), event.action());
        updateFriendList(event.userB(), event.userA(), event.action());

        if (event.action() == FriendshipAction.ADDED) {
            conversationService.createInitialChatRoom(
                event.userA(),
                event.userB(),
                new Timestamp(event.timestamp()).toLocalDateTime()
            );

            ConversationResponse convForA = conversationService.getConversationForUser(event.userA(), event.userB());
            ConversationResponse convForB = conversationService.getConversationForUser(event.userB(), event.userA());

            // Publish SocketEvents – socket-service will push to connected clients
            kafkaTemplate.send(socketEventsTopic, new SocketEvent(SocketEventType.CONVERSATION, event.userA(), "/queue/conversations", convForA));
            kafkaTemplate.send(socketEventsTopic, new SocketEvent(SocketEventType.CONVERSATION, event.userB(), "/queue/conversations", convForB));

            log.info("Published CONVERSATION socket events for users {} and {}", event.userA(), event.userB());
        }
    }

    private void updateFriendList(String targetUserId, String friendIdToModify, FriendshipAction action) {
        Query query = new Query(Criteria.where("id").is(targetUserId));
        Update update = new Update();

        if (action == FriendshipAction.ADDED) {
            update.addToSet("friendIds", friendIdToModify);
        } else if (action == FriendshipAction.REMOVED) {
            update.pull("friendIds", friendIdToModify);
        }

        mongoTemplate.updateFirst(query, update, ChatUser.class);
        log.debug("Updated friend list for {} (action: {}, friend: {})", targetUserId, action, friendIdToModify);
    }
}
