package com.bondhub.messageservice.consumer;

import com.bondhub.common.enums.FriendshipAction;
import com.bondhub.common.event.friend.FriendshipChangedEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.service.conversation.ConversationService;
import com.bondhub.common.utils.S3Util;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipMirrorConsumer {

    private final MongoTemplate mongoTemplate;
    private final ConversationService conversationService;
    private final ChatUserRepository chatUserRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @KafkaListener(topics = "${kafka.topics.friend-events.friendship-changed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleFriendshipChanged(FriendshipChangedEvent event) {
        log.info("Received FriendshipChangedEvent: userA={}, userB={}, action={}", event.userA(), event.userB(), event.action());

        updateFriendList(event.userA(), event.userB(), event.action());
        updateFriendList(event.userB(), event.userA(), event.action());

        if (event.action() == FriendshipAction.ADDED) {
            // Lấy hoặc khởi tạo phòng chat chung
            Conversation room = conversationService.getOrCreateDirectConversation(event.userA(), event.userB());

            // Lấy thông tin user để build ConversationResponse cho cả 2 phía
            Map<String, ChatUser> userCache = chatUserRepository
                    .findAllById(Arrays.asList(event.userA(), event.userB()))
                    .stream()
                    .collect(Collectors.toMap(ChatUser::getId, u -> u));

            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

            ConversationResponse convForA = buildMinimalResponse(room, event.userA(), event.userB(), userCache, baseUrl);
            ConversationResponse convForB = buildMinimalResponse(room, event.userB(), event.userA(), userCache, baseUrl);

            // Publish SocketEvents – socket-service will push to connected clients
            kafkaTemplate.send(socketEventsTopic, new SocketEvent(SocketEventType.CONVERSATION, event.userA(), "/queue/conversations", convForA));
            kafkaTemplate.send(socketEventsTopic, new SocketEvent(SocketEventType.CONVERSATION, event.userB(), "/queue/conversations", convForB));
            log.info("Published CONVERSATION socket events for users {} and {}", event.userA(), event.userB());
        }

        // --- BROADCAST FRIENDSHIP_UPDATED CHO TẤT CẢ ACTIONS ĐỂ FE CẬP NHẬT GIAO DIỆN ---
        String status = event.action().name();
        if (event.action() == FriendshipAction.REQUESTED) status = "PENDING";
        if (event.action() == FriendshipAction.ADDED) status = "ACCEPTED";
        if (event.action() == FriendshipAction.REMOVED) status = "CANCELLED";

        Map<String, Object> payloadForA = Map.of(
                "type", "FRIENDSHIP_UPDATED",
                "targetUserId", event.userA(),
                "payload", Map.of(
                        "partnerId", event.userB(), 
                        "status", status,
                        "friendshipId", event.friendshipId() != null ? event.friendshipId() : "",
                        "requestedBy", event.userA(),
                        "receivedBy", event.userB()
                )
        );
        Map<String, Object> payloadForB = Map.of(
                "type", "FRIENDSHIP_UPDATED",
                "targetUserId", event.userB(),
                "payload", Map.of(
                        "partnerId", event.userA(), 
                        "status", status,
                        "friendshipId", event.friendshipId() != null ? event.friendshipId() : "",
                        "requestedBy", event.userA(),
                        "receivedBy", event.userB()
                )
        );
        kafkaTemplate.send(socketEventsTopic, new SocketEvent(SocketEventType.CONVERSATION, event.userA(), "/queue/friendship-updates", payloadForA));
        kafkaTemplate.send(socketEventsTopic, new SocketEvent(SocketEventType.CONVERSATION, event.userB(), "/queue/friendship-updates", payloadForB));
        log.info("Published FRIENDSHIP_UPDATED {} for users {} and {}", status, event.userA(), event.userB());
    }

    /**
     * Build ConversationResponse tối giản (không cần securityUtil context)
     * để push xuống client ngay sau khi kết bạn.
     */
    private ConversationResponse buildMinimalResponse(
            Conversation room, String viewerId, String partnerId,
            Map<String, ChatUser> userCache, String baseUrl) {

        ChatUser partner = userCache.getOrDefault(partnerId,
                ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());
        LastMessageInfo last = room.getLastMessage();

        return ConversationResponse.builder()
                .id(room.getId())
                .name(partner.getFullName())
                .avatar(partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null)
                .status(partner.getStatus())
                .isGroup(false)
                .lastMessage(last != null ? last.getContent() : "")
                .lastMessageTime(last != null ? last.getTimestamp() : null)
                .friendshipStatus("ACCEPTED")
                .unreadCount(room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(viewerId, 0) : 0)
                .members(Collections.emptyList())
                .build();
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
