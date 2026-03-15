package com.bondhub.messageservice.consumer;

import com.bondhub.common.enums.FriendshipAction;
import com.bondhub.common.event.friend.FriendshipChangedEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.bondhub.messageservice.dto.response.ConversationResponse;
import java.sql.Timestamp;
@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipMirrorConsumer {

    private final MongoTemplate mongoTemplate;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "${kafka.topics.friend-events.friendship-changed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleFriendshipChanged(FriendshipChangedEvent event) {
        log.info("Received FriendshipChangedEvent: userA={}, userB={}, action={}, timestamp={}",
                event.userA(), event.userB(), event.action(), event.timestamp());

        // Update User A's friendIds list
        updateFriendList(event.userA(), event.userB(), event.action());

        // Update User B's friendIds list
        updateFriendList(event.userB(), event.userA(), event.action());

        if (event.action() == FriendshipAction.ADDED) {
            conversationService.createInitialChatRoom(
                event.userA(),
                event.userB(),
                new Timestamp(event.timestamp()).toLocalDateTime()
            );
            
            ConversationResponse convForA = conversationService.getConversationForUser(event.userA(), event.userB());
            ConversationResponse convForB = conversationService.getConversationForUser(event.userB(), event.userA());
            
            // Notify both users to refresh their conversation list via JSON payload
            messagingTemplate.convertAndSendToUser(event.userA(), "/queue/conversations", convForA);
            messagingTemplate.convertAndSendToUser(event.userB(), "/queue/conversations", convForB);
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
