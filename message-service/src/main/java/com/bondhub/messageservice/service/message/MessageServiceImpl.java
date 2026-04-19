package com.bondhub.messageservice.service.message;

import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.dto.response.MessageSeenResponse;
import com.bondhub.messageservice.dto.response.ReplyMetadataResponse;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.AttachmentInfo;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.service.conversation.ConversationService;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.messageservice.publisher.MessageIndexEventPublisher;
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

import com.bondhub.messageservice.model.GroupSettings;
import com.bondhub.messageservice.model.LinkPreview;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.service.conversation.ConversationHelper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private static final Pattern JOIN_LINK_PATTERN = Pattern.compile("^https?://[^/]+/g/([a-zA-Z0-9_-]+)$");

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SecurityUtil securityUtil;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ConversationHelper conversationHelper;
    private final MessageIndexEventPublisher messageIndexEventPublisher;

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
        assertConversationMember(room, currentUserId);
        ConversationMember currentMember = room.getMembers().stream()
            .filter(m -> m.getUserId().equals(currentUserId))
            .findFirst().orElse(null);
        boolean isActive = currentMember != null && isActiveMember(currentMember);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        LocalDateTime memberJoinedAt = (currentMember != null && currentMember.getJoinedAt() != null)
            ? currentMember.getJoinedAt()
            : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime deletedBefore = (room.getDeletedBefore() != null)
            ? room.getDeletedBefore().getOrDefault(currentUserId, LocalDateTime.of(1970, 1, 1, 0, 0))
            : LocalDateTime.of(1970, 1, 1, 0, 0);
        Page<Message> messagePage = isActive
            ? messageRepository.findByConversationIdAndNotDeleted(conversationId, currentUserId, memberJoinedAt, deletedBefore, pageable)
            : messageRepository.findByConversationIdAndTypeAndNotDeleted(
                conversationId,
                currentUserId,
                MessageType.SYSTEM,
                deletedBefore,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))
            );

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        List<MessageResponse> dtos = messagePage.getContent().stream()
                .map(msg -> messageMapper.mapToMessageResponse(msg, baseUrl))
                .collect(Collectors.toList());

        enrichMessages(dtos);
        return PageResponse.fromPageData(messagePage, dtos);
    }

    @Override
    public PageResponse<List<MessageResponse>> findMediaMessages(String conversationId, List<String> types, int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertConversationMember(room, currentUserId);

        LocalDateTime deletedBefore = (room.getDeletedBefore() != null)
                ? room.getDeletedBefore().getOrDefault(currentUserId, LocalDateTime.of(1970, 1, 1, 0, 0))
                : LocalDateTime.of(1970, 1, 1, 0, 0);

        List<MessageType> messageTypes = types.stream()
                .map(t -> {
                    try { return MessageType.valueOf(t.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(t -> t != null)
                .collect(Collectors.toList());

        if (messageTypes.isEmpty()) {
            return PageResponse.fromPageData(Page.empty(), List.of());
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Message> messagePage = messageRepository.findByConversationIdAndTypesAndNotDeleted(
                conversationId, currentUserId, messageTypes, deletedBefore, pageable);

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        List<MessageResponse> dtos = messagePage.getContent().stream()
                .map(msg -> messageMapper.mapToMessageResponse(msg, baseUrl))
                .collect(Collectors.toList());

        return PageResponse.fromPageData(messagePage, dtos);
    }

    // ─────────────────────────── Gửi tin nhắn ───────────────────────────

    @Override
    public void sendMessage(String conversationId, MessageSendRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();

        // 1. Tìm phòng chat bằng ObjectId hoặc lazy creation
        Conversation room;

        if (conversationId != null) {
            room = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        } else if (request.recipientId() != null) {
            // Luồng Lazy Creation cho người lạ/chat mới
            room = conversationService.getOrCreateDirectConversation(currentUserId, request.recipientId());
        } else {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        assertActiveMember(room, currentUserId);
        conversationHelper.assertSettingAllowed(room, currentUserId, GroupSettings::isMemberCanSendMessages);

        // 2. Enrich sender info
        ChatUser sender = chatUserRepository.findById(currentUserId).orElse(null);

        // 3. Validate request & resolve type
        validateMessageRequest(request);
        LinkPreview linkPreview = null;

        // Kiểm tra join link nếu không có attachments
        if (request.attachments() == null || request.attachments().isEmpty()) {
            String trimmedContent = request.content() != null ? request.content().trim() : "";
            Matcher joinLinkMatcher = JOIN_LINK_PATTERN.matcher(trimmedContent);
            if (joinLinkMatcher.matches()) {
                String token = joinLinkMatcher.group(1);
                linkPreview = buildJoinLinkPreview(trimmedContent, token);
            }
        }

        MessageType messageType = resolveMessageType(request, linkPreview);
        List<AttachmentInfo> attachments = mapAttachments(request);

        Message message = Message.builder()
                .conversationId(room.getId())
                .senderId(currentUserId)
                .senderName(sender != null ? sender.getFullName() : null)
                .senderAvatar(sender != null ? sender.getAvatar() : null)
                .content(request.content())
                .clientMessageId(request.clientMessageId())
                .replyTo(request.replyTo())
                .isForwarded(request.isForwarded())
                .type(messageType)
                .attachments(attachments)
                .linkPreview(linkPreview)
                .build();
        messageRepository.save(message);

        // 4. Xây dựng last message preview
        String previewContent = buildPreviewContent(message);
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
            .filter(this::isActiveMember)
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

        finalRoom.getMembers().stream()
            .filter(this::isActiveMember)
            .forEach(member -> {
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

        messageIndexEventPublisher.publishIndexRequest(message);
    }

    // ─────────────────────────── Thu hồi / Xóa ───────────────────────────

    @Override
    public void revokeMessage(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getSenderId().equals(currentUserId)) {
            throw new AppException(ErrorCode.CHAT_NOT_SENDER);
        }

        if (message.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            throw new AppException(ErrorCode.MESSAGE_REVOKE_TIME_EXCEEDED);
        }

        message.setStatus(MessageStatus.REVOKED);
        message.setContent(null);
        message.setReplyTo(null);
        messageRepository.save(message);

        // Update all messages that reply to this message
        Query replyQuery = new Query(Criteria.where("replyTo.messageId").is(messageId));
        Update replyUpdate = new Update()
                .set("replyTo.content", null)
                .set("replyTo.type", MessageType.CHAT);
        mongoTemplate.updateMulti(replyQuery, replyUpdate, Message.class);

        updateLastMessageStatus(message, MessageStatus.REVOKED);
        broadcastStatusChange(message.getConversationId(), messageId, MessageStatus.REVOKED);

        messageIndexEventPublisher.publishIndexRequest(message);
    }

    @Override
    public void deleteMessageForMe(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("deletedBy", currentUserId);
        mongoTemplate.updateFirst(query, update, Message.class);

        messageRepository.findById(messageId).ifPresent(messageIndexEventPublisher::publishIndexRequest);
    }

    @Override
    public void deleteGroupMemberMessage(String conversationId, String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!room.isGroup()) throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);

        ConversationMember actor = room.getMembers().stream()
                .filter(m -> m.getUserId().equals(currentUserId) && isActiveMember(m))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND));

        MemberRole actorRole = actor.getRole() != null ? actor.getRole() : MemberRole.MEMBER;
        if (actorRole == MemberRole.MEMBER) {
            throw new AppException(ErrorCode.CHAT_NOT_GROUP_MANAGER);
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getConversationId().equals(conversationId)) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        if (message.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            throw new AppException(ErrorCode.MESSAGE_REVOKE_TIME_EXCEEDED);
        }

        if (actorRole == MemberRole.ADMIN) {
            room.getMembers().stream()
                    .filter(m -> m.getUserId().equals(message.getSenderId()))
                    .findFirst()
                    .ifPresent(sender -> {
                        MemberRole senderRole = sender.getRole() != null ? sender.getRole() : MemberRole.MEMBER;
                        if (senderRole == MemberRole.OWNER) {
                            throw new AppException(ErrorCode.CHAT_ADMIN_CANNOT_DELETE_OWNER_MESSAGE);
                        }
                    });
        }

        message.setStatus(MessageStatus.DELETED_BY_ADMIN);
        message.setDeletedByAdminId(currentUserId);
        message.setContent(null);
        message.setReplyTo(null);
        messageRepository.save(message);

        Query replyQuery = new Query(Criteria.where("replyTo.messageId").is(messageId));
        Update replyUpdate = new Update()
                .set("replyTo.content", null)
                .set("replyTo.type", MessageType.CHAT);
        mongoTemplate.updateMulti(replyQuery, replyUpdate, Message.class);

        updateLastMessageStatus(message, MessageStatus.DELETED_BY_ADMIN);
        broadcastStatusChange(conversationId, messageId, MessageStatus.DELETED_BY_ADMIN, currentUserId);
    }

    @Override
    public void toggleReaction(String messageId, String emoji) {
        String currentUserId = securityUtil.getCurrentUserId();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        
        Conversation room = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertActiveMember(room, currentUserId);


        Map<String, List<String>> reactions = message.getReactions();
        if (reactions == null) {
            reactions = new HashMap<>();
        }

        List<String> users = reactions.computeIfAbsent(emoji, k -> new ArrayList<>());
        users.add(currentUserId);
        message.setReactions(reactions);
        messageRepository.save(message);

        // Broadcast reaction update to all members
        broadcastReactionUpdate(room, messageId, message.getReactions());
    }

    @Override
    public void removeAllMyReactions(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        Conversation room = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertActiveMember(room, currentUserId);

        Map<String, List<String>> reactions = message.getReactions();
        if (reactions == null || reactions.isEmpty()) return;

        // Remove ALL occurrences of current user from every emoji list
        reactions.entrySet().removeIf(entry -> {
            entry.getValue().removeAll(List.of(currentUserId));
            return entry.getValue().isEmpty();
        });

        message.setReactions(reactions.isEmpty() ? null : reactions);
        messageRepository.save(message);

        broadcastReactionUpdate(room, messageId, message.getReactions());
    }

    @Override
    public List<MessageSeenResponse> getSeenMembers(String conversationId, String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertConversationMember(room, currentUserId);

        Message targetMessage = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!targetMessage.getConversationId().equals(conversationId)) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        if (!targetMessage.getSenderId().equals(currentUserId)) {
            throw new AppException(ErrorCode.CHAT_NOT_SENDER);
        }

        // Collect lastReadMessageIds from active members (exclude sender of the target message)
        List<ConversationMember> activeMembers = room.getMembers().stream()
                .filter(this::isActiveMember)
                .filter(m -> !m.getUserId().equals(targetMessage.getSenderId()))
                .filter(m -> m.getLastReadMessageId() != null)
                .toList();

        if (activeMembers.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch-fetch all lastRead messages and filter those with createdAt >= targetMessage.createdAt
        Set<String> lastReadIds = activeMembers.stream()
                .map(ConversationMember::getLastReadMessageId)
                .collect(Collectors.toSet());

        Query query = new Query(
                Criteria.where("id").in(lastReadIds)
                        .and("createdAt").gte(targetMessage.getCreatedAt())
        );
        List<Message> seenMessages = mongoTemplate.find(query, Message.class);
        Set<String> seenMessageIds = seenMessages.stream()
                .map(Message::getId)
                .collect(Collectors.toSet());

        // Find members whose lastReadMessageId indicates they have seen the target message
        List<String> seenUserIds = activeMembers.stream()
                .filter(m -> seenMessageIds.contains(m.getLastReadMessageId()))
                .map(ConversationMember::getUserId)
                .toList();

        if (seenUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, ChatUser> userMap = chatUserRepository.findAllById(seenUserIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        return seenUserIds.stream()
                .map(userId -> {
                    ChatUser user = userMap.get(userId);
                    return MessageSeenResponse.builder()
                            .userId(userId)
                            .fullName(user != null ? user.getFullName() : null)
                            .avatar(user != null && user.getAvatar() != null
                                    ? baseUrl + user.getAvatar()
                                    : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    /**
     * Kiểm tra user có trong members của conversation không.
     * Nếu không → throw UNAUTHORIZED (403).
     */
    private void assertConversationMember(Conversation room, String userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }
    }

    private void assertActiveMember(Conversation room, String userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId) && isActiveMember(m));
        if (!isMember) {
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }
    }

    private boolean isActiveMember(ConversationMember member) {
        return !Boolean.FALSE.equals(member.getActive());
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

    private void updateLastMessageStatus(Message msg, MessageStatus newStatus) {
        Query query = new Query(Criteria.where("id").is(msg.getConversationId())
                .and("lastMessage.messageId").is(msg.getId()));
        Update update = new Update()
                .set("lastMessage.content", null)
                .set("lastMessage.status", newStatus);
        mongoTemplate.updateFirst(query, update, Conversation.class);
    }

    private void broadcastStatusChange(String conversationId, String messageId, MessageStatus newStatus) {
        broadcastStatusChange(conversationId, messageId, newStatus, null);
    }

    private void broadcastStatusChange(String conversationId, String messageId, MessageStatus newStatus, String deletedByAdminId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) return;

        for (ConversationMember member : conversation.getMembers()) {
            if (Boolean.FALSE.equals(member.getActive())) continue;

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "MESSAGE_STATUS_UPDATE");
            payload.put("conversationId", conversationId);
            payload.put("messageId", messageId);
            payload.put("newStatus", newStatus);
            if (deletedByAdminId != null) {
                payload.put("deletedByAdminId", deletedByAdminId);
            }

            kafkaTemplate.send(socketEventsTopic,
                    new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                            "/queue/status-updates", payload));
        }
    }

    private void broadcastReactionUpdate(Conversation room, String messageId, Map<String, List<String>> reactions) {
        for (ConversationMember member : room.getMembers()) {
            if (!isActiveMember(member)) continue;
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "REACTION_UPDATE");
            payload.put("conversationId", room.getId());
            payload.put("messageId", messageId);
            payload.put("reactions", reactions);

            kafkaTemplate.send(socketEventsTopic,
                    new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                            "/queue/reactions", payload));
        }
    }

    private LinkPreview buildJoinLinkPreview(String url, String token) {
        try {
            Conversation target = conversationRepository.findByJoinLinkToken(token).orElse(null);
            if (target == null || !target.isGroup()) return null;

            GroupSettings settings = target.getSettings();
            if (settings == null || !settings.isJoinByLinkEnabled()) return null;

            Set<ConversationMember> activeMembers = target.getMembers().stream()
                    .filter(m -> !Boolean.FALSE.equals(m.getActive()))
                    .collect(Collectors.toSet());

            Set<String> memberIds = activeMembers.stream()
                    .map(ConversationMember::getUserId).collect(Collectors.toSet());
            Map<String, ChatUser> userCache = chatUserRepository.findAllById(memberIds).stream()
                    .collect(Collectors.toMap(ChatUser::getId, u -> u));

            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
            List<LinkPreview.MemberSnapshot> previews = activeMembers.stream()
                    .map(ConversationMember::getUserId)
                    .limit(5)
                    .map(userCache::get)
                    .filter(Objects::nonNull)
                    .map(u -> LinkPreview.MemberSnapshot.builder()
                            .name(u.getFullName())
                            .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                            .build())
                    .toList();

            String groupName = target.getName();
            if (groupName == null || groupName.isBlank()) {
                groupName = conversationHelper.getDynamicGroupName(target, null, userCache);
            }

            return LinkPreview.builder()
                    .url(url)
                    .token(token)
                    .groupName(groupName)
                    .groupAvatar(target.getAvatar() != null ? baseUrl + target.getAvatar() : null)
                    .memberCount(activeMembers.size())
                    .memberPreviews(previews)
                    .build();
        } catch (Exception e) {
            log.warn("[Chat] Failed to build join link preview for token {}: {}", token, e.getMessage());
            return null;
        }
    }

    // ───────────── Attachment / Type helpers ─────────────

    private void validateMessageRequest(MessageSendRequest request) {
        boolean hasContent = request.content() != null && !request.content().isBlank();
        boolean hasAttachments = request.attachments() != null && !request.attachments().isEmpty();
        if (!hasContent && !hasAttachments) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private MessageType resolveMessageType(MessageSendRequest request, LinkPreview linkPreview) {
        if (linkPreview != null) return MessageType.LINK;
        if (request.attachments() == null || request.attachments().isEmpty()) return MessageType.CHAT;

        String contentType = request.attachments().get(0).contentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) return MessageType.IMAGE;
            if (contentType.startsWith("video/")) return MessageType.VIDEO;
        }
        return MessageType.FILE;
    }

    private List<AttachmentInfo> mapAttachments(MessageSendRequest request) {
        if (request.attachments() == null || request.attachments().isEmpty()) return List.of();
        return request.attachments().stream()
                .map(a -> AttachmentInfo.builder()
                        .key(a.key())
                        .url(a.url())
                        .fileName(a.fileName())
                        .originalFileName(a.originalFileName())
                        .contentType(a.contentType())
                        .size(a.size())
                        .build())
                .toList();
    }

    private String buildPreviewContent(Message message) {
        return switch (message.getType() == null ? MessageType.CHAT : message.getType()) {
            case IMAGE -> "[Hình ảnh]";
            case VIDEO -> "[Video]";
            case FILE -> "[Tệp]";
            case LINK -> "[Liên kết]";
            default -> message.getContent();
        };
    }

}
