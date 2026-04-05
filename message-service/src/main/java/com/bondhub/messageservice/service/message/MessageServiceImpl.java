package com.bondhub.messageservice.service.message;

import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.dto.response.ReplyMetadataResponse;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.mapper.MessageMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SecurityUtil securityUtil;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    // ─────────────────────────── Lấy tin nhắn ───────────────────────────

    @Override
    public PageResponse<List<MessageResponse>> findChatMessages(String conversationId, int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();

        // Kiểm tra quyền: user phải là thành viên của phòng chat
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertMember(room, currentUserId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Message> messagePage = messageRepository.findByConversationIdAndNotDeleted(
                conversationId, currentUserId, pageable);

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        List<MessageResponse> dtos = messagePage.getContent().stream()
                .map(msg -> messageMapper.mapToMessageResponse(msg, baseUrl))
                .collect(Collectors.toList());

        enrichMessages(dtos);
        return PageResponse.fromPageData(messagePage, dtos);
    }

    // ─────────────────────────── Gửi tin nhắn ───────────────────────────

    @Override
    public void sendMessage(String conversationId, MessageSendRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();

        // 1. Tìm phòng chat bằng ObjectId — kiểm tra quyền thành viên
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertMember(room, currentUserId);

        // 2. Enrich sender info
        ChatUser sender = chatUserRepository.findById(currentUserId).orElse(null);

        // 3. Lưu tin nhắn
        Message message = Message.builder()
                .conversationId(room.getId())
                .senderId(currentUserId)
                .senderName(sender != null ? sender.getFullName() : null)
                .senderAvatar(sender != null ? sender.getAvatar() : null)
                .content(request.content())
                .clientMessageId(request.clientMessageId())
                .replyTo(request.replyTo())
                .isForwarded(request.isForwarded())
                .type(MessageType.CHAT)
                .build();
        messageRepository.save(message);

        // 4. Xây dựng last message preview
        String previewContent = switch (message.getType() == null ? MessageType.CHAT : message.getType()) {
            case IMAGE -> "[IMAGE]";
            case FILE -> "[FILE]";
            default -> message.getContent();
        };
        LastMessageInfo lastInfo = LastMessageInfo.builder()
                .messageId(message.getId())
                .senderId(currentUserId)
                .content(previewContent)
                .timestamp(message.getCreatedAt())
                .type(message.getType())
                .status(message.getStatus())
                .build();

        // 5. Cập nhật lastMessage + tăng unreadCount cho tất cả member trừ sender
        Query query = new Query(Criteria.where("id").is(room.getId()));
        Update update = new Update().set("lastMessage", lastInfo);
        room.getMembers().stream()
                .filter(m -> !m.getUserId().equals(currentUserId))
                .forEach(m -> update.inc("unreadCounts." + m.getUserId(), 1));

        Conversation updatedRoom = mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class);

        log.info("[Chat] Saved message & updated conversation state for room: {}", room.getId());

        // 6. Push notification qua Kafka cho từng thành viên
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        Conversation finalRoom = updatedRoom != null ? updatedRoom : room;

        List<ChatNotification> notifPrototypes = new ArrayList<>(
                List.of(messageMapper.mapToChatNotification(message, baseUrl, 0)));
        enrichNotifications(notifPrototypes);
        ChatNotification baseNotif = notifPrototypes.get(0);

        finalRoom.getMembers().forEach(member -> {
            boolean isFromMe = member.getUserId().equals(currentUserId);
            Integer unreadCount = finalRoom.getUnreadCounts().getOrDefault(member.getUserId(), 0);

            ChatNotification personalNotif = baseNotif.toBuilder()
                    .isFromMe(isFromMe)
                    .unreadCount(unreadCount)
                    .build();

            kafkaTemplate.send(socketEventsTopic,
                    new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                            "/queue/messages", personalNotif));
        });

        // 7. Ingest vào AI system
        log.info("[Kafka] Sending user message to 'message-created' for AI ingestion.");
        kafkaTemplate.send("message-created", AiMessageSaveEvent.builder()
                .userId(currentUserId)
                .chatId(room.getId())
                .content(message.getContent())
                .build());
    }

    // ─────────────────────────── Thu hồi / Xóa ───────────────────────────

    @Override
    public void revokeMessage(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getSenderId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        message.setStatus(MessageStatus.REVOKED);
        message.setContent(null);
        message.setReplyTo(null);
        messageRepository.save(message);

        updateLastMessageIfRevoked(message);
        broadcastStatusChange(message.getConversationId(), messageId, MessageStatus.REVOKED);
    }

    @Override
    public void deleteMessageForMe(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("deletedBy", currentUserId);
        mongoTemplate.updateFirst(query, update, Message.class);
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    /**
     * Kiểm tra user có trong members của conversation không.
     * Nếu không → throw UNAUTHORIZED (403).
     */
    private void assertMember(Conversation room, String userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void enrichMessages(List<MessageResponse> dtos) {
        Set<String> userIds = new HashSet<>();
        dtos.forEach(d -> {
            userIds.add(d.senderId());
            if (d.replyTo() != null) userIds.add(d.replyTo().senderId());
        });
        if (userIds.isEmpty()) return;

        Map<String, ChatUser> userMap = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        for (int i = 0; i < dtos.size(); i++) {
            MessageResponse d = dtos.get(i);
            MessageResponse enriched = d;
            ChatUser sender = userMap.get(d.senderId());
            if (sender != null) {
                enriched = enriched.withSenderName(sender.getFullName())
                        .withSenderAvatar(sender.getAvatar() != null
                                ? baseUrl + sender.getAvatar() : null);
            }
            if (d.replyTo() != null) {
                ChatUser replySender = userMap.get(d.replyTo().senderId());
                if (replySender != null) {
                    enriched = enriched.withReplyTo(d.replyTo().withSenderName(replySender.getFullName()));
                }
            }
            dtos.set(i, enriched);
        }
    }

    private void enrichNotifications(List<ChatNotification> notifs) {
        Set<String> userIds = new HashSet<>();
        notifs.forEach(n -> {
            userIds.add(n.senderId());
            if (n.replyTo() != null) userIds.add(n.replyTo().senderId());
        });
        if (userIds.isEmpty()) return;

        Map<String, ChatUser> userMap = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        for (int i = 0; i < notifs.size(); i++) {
            ChatNotification n = notifs.get(i);
            ChatNotification enriched = n;
            ChatUser sender = userMap.get(n.senderId());
            if (sender != null) {
                enriched = enriched.withSenderName(sender.getFullName())
                        .withSenderAvatar(sender.getAvatar() != null
                                ? baseUrl + sender.getAvatar() : null);
            }
            if (n.replyTo() != null) {
                ChatUser replySender = userMap.get(n.replyTo().senderId());
                if (replySender != null) {
                    ReplyMetadataResponse enrichedReply = n.replyTo().withSenderName(replySender.getFullName());
                    enriched = enriched.withReplyTo(enrichedReply);
                }
            }
            notifs.set(i, enriched);
        }
    }

    private void updateLastMessageIfRevoked(Message revokedMsg) {
        Query query = new Query(Criteria.where("id").is(revokedMsg.getConversationId())
                .and("lastMessage.messageId").is(revokedMsg.getId()));
        Update update = new Update()
                .set("lastMessage.content", "Tin nhắn đã được thu hồi")
                .set("lastMessage.status", MessageStatus.REVOKED);
        mongoTemplate.updateFirst(query, update, Conversation.class);
    }

    @Override
    public Message sendSystemMessage(String conversationId, String actorId, String actorName,
                                  SystemActionType action, Map<String, Object> extraMetadata) {
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", action.name());
        metadata.put("actorId", actorId);
        metadata.put("actorName", actorName);
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(actorId)
                .senderName(actorName)
                .type(MessageType.SYSTEM)
                .metadata(metadata)
                .build();
        message.setCreatedAt(now);
        Message savedMessage = messageRepository.save(message);

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null) {
            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
            ChatNotification notification = messageMapper.mapToChatNotification(savedMessage, baseUrl, 0);

            conversation.getMembers().forEach(member -> {
                kafkaTemplate.send(socketEventsTopic,
                        new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                                "/queue/messages", notification));
            });
        }

        return savedMessage;
    }

    private void broadcastStatusChange(String conversationId, String messageId, MessageStatus newStatus) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) return;

        for (ConversationMember member : conversation.getMembers()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "MESSAGE_STATUS_UPDATE");
            payload.put("conversationId", conversationId);
            payload.put("messageId", messageId);
            payload.put("newStatus", newStatus);

            kafkaTemplate.send(socketEventsTopic,
                    new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                            "/queue/status-updates", payload));
        }
    }
}
