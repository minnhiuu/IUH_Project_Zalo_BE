package com.bondhub.messageservice.service.message;

import com.bondhub.common.enums.MessageType;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.messageservice.mapper.MessageMapper;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.service.conversation.ConversationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemMessageServiceImpl implements SystemMessageService {

    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final S3UtilV2 s3UtilV2;
    private final RawNotificationEventPublisher rawNotificationEventPublisher;
    private final ConversationHelper conversationHelper;
    private final ChatUserRepository chatUserRepository;



    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    @Override
    public void sendSystemMessage(String conversationId, String actorId, String actorName, String actorAvatar,
                                  SystemActionType action, Map<String, Object> extraMetadata) {
        sendSystemMessage(conversationId, actorId, actorName, actorAvatar, action, extraMetadata, null);
    }

    @Override
    public void sendSystemMessage(String conversationId, String actorId, String actorName, String actorAvatar,
                                  SystemActionType action, Map<String, Object> extraMetadata,
                                  Set<String> recipientUserIds) {
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", action.name());
        metadata.put("actorName", actorName);
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(actorId)
                .senderName(actorName)
                .senderAvatar(actorAvatar)
                .type(MessageType.SYSTEM)
                .metadata(metadata)
                .visibleTo(recipientUserIds != null && !recipientUserIds.isEmpty()
                        ? new HashSet<>(recipientUserIds) : null)
                .build();
        message.setCreatedAt(now);
        Message savedMessage = messageRepository.save(message);

        boolean isRestricted = recipientUserIds != null && !recipientUserIds.isEmpty();

        boolean isNegativeAction = action == SystemActionType.LEAVE_GROUP
                || action == SystemActionType.DISBAND_GROUP
                || action == SystemActionType.REMOVE_MEMBER
                || action == SystemActionType.BLOCK_MEMBER
                || action == SystemActionType.ADD_MEMBERS
                || action == SystemActionType.CREATE_GROUP;

        boolean isQuietModeAutoReply = action == SystemActionType.DND_AUTO_REPLY;

        Conversation room;
        Query query = new Query(Criteria.where("id").is(conversationId));

        if (isQuietModeAutoReply) {
            // Skip updating lastMessage for DND_AUTO_REPLY to keep sidebar as is
            room = mongoTemplate.findOne(query, Conversation.class);
        } else if (isRestricted) {
            Conversation existing = mongoTemplate.findOne(query, Conversation.class);
            LocalDateTime preservedTimestamp = (existing != null && existing.getLastMessage() != null
                    && existing.getLastMessage().getTimestamp() != null)
                    ? existing.getLastMessage().getTimestamp()
                    : now;

            Update update = new Update().set("lastMessage", LastMessageInfo.builder()
                    .messageId(savedMessage.getId())
                    .senderId(actorId)
                    .content(null)
                    .timestamp(preservedTimestamp)
                    .type(MessageType.SYSTEM)
                    .metadata(savedMessage.getMetadata())
                    .visibleTo(new HashSet<>(recipientUserIds))
                    .build());

            room = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Conversation.class);
        } else if (isNegativeAction) {
            Conversation existing = mongoTemplate.findOne(query, Conversation.class);
            LocalDateTime preservedTimestamp = (existing != null && existing.getLastMessage() != null
                    && existing.getLastMessage().getTimestamp() != null)
                    ? existing.getLastMessage().getTimestamp()
                    : now;

            Update update = new Update().set("lastMessage", LastMessageInfo.builder()
                    .messageId(savedMessage.getId())
                    .senderId(actorId)
                    .content(null)
                    .timestamp(preservedTimestamp)
                    .type(MessageType.SYSTEM)
                    .metadata(savedMessage.getMetadata())
                    .build());

            room = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Conversation.class);
        } else {
            Update update = new Update().set("lastMessage", LastMessageInfo.builder()
                    .messageId(savedMessage.getId())
                    .senderId(actorId)
                    .content(null)
                    .timestamp(savedMessage.getCreatedAt())
                    .type(MessageType.SYSTEM)
                    .metadata(savedMessage.getMetadata())
                    .build());

            room = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Conversation.class);
        }

        if (room != null) {
            String baseUrl = s3UtilV2.getS3BaseUrl();

            room.getMembers().forEach(member -> {
                boolean isInactive = Boolean.FALSE.equals(member.getActive());
                if (isInactive) {
                    boolean isTarget = false;
                    if (metadata.get("targetIds") instanceof Collection<?> tIds) {
                        isTarget = tIds.stream().map(String::valueOf).anyMatch(id -> id.equals(member.getUserId()));
                    }
                    boolean isActor = member.getUserId().equals(actorId);
                    if (!isTarget && !isActor) {
                        return;
                    }
                }

                if (recipientUserIds != null && !recipientUserIds.contains(member.getUserId())) {
                    return;
                }

                Integer currentUnread = room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(member.getUserId(), 0) : 0;
                boolean isFromMe = member.getUserId().equals(actorId);

                ChatNotification notification = messageMapper.mapToChatNotification(savedMessage, baseUrl, currentUnread);
                notification = notification.toBuilder().isFromMe(isFromMe).build();

                kafkaTemplate.send(socketEventsTopic,
                        new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                                "/queue/messages", notification));

                // Publish RawNotificationEvent for push notifications
                // Skip for sender, negative actions, restricted visibility, and Quiet Mode Auto Reply
                if (!isFromMe && !isNegativeAction && !isQuietModeAutoReply) {
                    String dynamicGroupName = room.getName();
                    if (room.isGroup() && (dynamicGroupName == null || dynamicGroupName.isBlank())) {
                        Set<String> memberIdsToFetch = room.getMembers().stream()
                                .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                                .map(ConversationMember::getUserId)
                                .limit(5)
                                .collect(Collectors.toSet());
                        Map<String, ChatUser> groupUserCache = chatUserRepository.findAllById(memberIdsToFetch).stream()
                                .collect(Collectors.toMap(ChatUser::getId, u -> u));
                        dynamicGroupName = conversationHelper.getDynamicGroupName(room, member.getUserId(), groupUserCache);
                    }

                    RawNotificationEvent rawEvent = RawNotificationEvent.builder()
                            .recipientId(member.getUserId())
                            .actorId(actorId)
                            .actorName(actorName != null ? actorName : "Hệ thống")
                            .actorAvatar(actorAvatar != null && !actorAvatar.isEmpty() ? (actorAvatar.startsWith("http") ? actorAvatar : baseUrl + actorAvatar) : "")
                            .type(NotificationType.SYSTEM)
                            .referenceId(savedMessage.getId())
                            .payload(Map.of(
                                    "conversationId", conversationId,
                                    "messageId", savedMessage.getId(),
                                    "action", action.name(),
                                    "isGroup", room.isGroup(),
                                    "groupName", dynamicGroupName != null ? dynamicGroupName : "",
                                    "conversationAvatar", room.isGroup() 
                                            ? (room.getAvatar() != null ? baseUrl + room.getAvatar() : (actorAvatar != null && !actorAvatar.isEmpty() ? (actorAvatar.startsWith("http") ? actorAvatar : baseUrl + actorAvatar) : "")) 
                                            : (actorAvatar != null && !actorAvatar.isEmpty() ? (actorAvatar.startsWith("http") ? actorAvatar : baseUrl + actorAvatar) : ""),
                                    "metadata", savedMessage.getMetadata() != null ? savedMessage.getMetadata() : Collections.emptyMap()
                            ))
                            .occurredAt(savedMessage.getCreatedAt())
                            .build();

                    rawNotificationEventPublisher.publish(rawEvent);
                }
            });
        }
    }
}
