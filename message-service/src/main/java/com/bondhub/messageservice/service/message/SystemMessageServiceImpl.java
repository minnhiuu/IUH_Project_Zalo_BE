package com.bondhub.messageservice.service.message;

import com.bondhub.common.enums.MessageType;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.mapper.MessageMapper;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.repository.MessageRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemMessageServiceImpl implements SystemMessageService {

    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

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
                || action == SystemActionType.REMOVE_MEMBER;

        Conversation room;
        Query query = new Query(Criteria.where("id").is(conversationId));

        if (isRestricted) {
            room = mongoTemplate.findOne(query, Conversation.class);
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
            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

            room.getMembers().forEach(member -> {
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
            });
        }
    }
}
