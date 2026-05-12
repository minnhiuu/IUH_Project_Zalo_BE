package com.bondhub.messageservice.service.message;

import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.dto.response.CursorPageResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;
import com.bondhub.messageservice.dto.response.MessageSeenResponse;
import com.bondhub.messageservice.dto.response.ReplyMetadataResponse;
import com.bondhub.messageservice.dto.response.*;
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
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.common.event.ai.AiMessageSaveEvent;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.messageservice.publisher.MessageIndexEventPublisher;
import com.bondhub.messageservice.publisher.ChatInteractionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bondhub.common.dto.PageResponse;
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
    private final S3UtilV2 s3UtilV2;
    private final MessageIndexEventPublisher messageIndexEventPublisher;
    private final ChatInteractionEventPublisher chatInteractionEventPublisher;
    private final RawNotificationEventPublisher rawNotificationEventPublisher;

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

        String baseUrl = s3UtilV2.getS3BaseUrl();
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

        String baseUrl = s3UtilV2.getS3BaseUrl();
        List<MessageResponse> dtos = messagePage.getContent().stream()
                .map(msg -> messageMapper.mapToMessageResponse(msg, baseUrl))
                .collect(Collectors.toList());

        return PageResponse.fromPageData(messagePage, dtos);
    }

    @Override
    public CursorPageResponse<MessageResponse> findChatMessagesV2(
            String conversationId, String cursor, int limit, String direction, String aroundMessageId) {

        String currentUserId = securityUtil.getCurrentUserId();
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertConversationMember(room, currentUserId);

        if (aroundMessageId != null) {
            Message target = messageRepository.findById(aroundMessageId)
                    .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

            if (!target.getConversationId().equals(conversationId)) {
                throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
            }

            List<Message> older = findOlder(room, target.getCreatedAt(), limit / 2, currentUserId);
            List<Message> newer = findNewer(room, target.getCreatedAt(), limit / 2, currentUserId);

            List<Message> combined = new ArrayList<>();
            combined.addAll(newer);
            combined.add(target);
            combined.addAll(older);

            return buildCursorResponse(combined, limit, true);
        }

        return standardCursorPagination(room, cursor, direction, limit, currentUserId);
    }

    private CursorPageResponse<MessageResponse> standardCursorPagination(
            Conversation room, String cursor, String direction, int limit, String currentUserId) {

        Query query = new Query();
        List<Criteria> allCriteria = new ArrayList<>();
        allCriteria.add(Criteria.where("conversationId").is(room.getId()));

        LocalDateTime cursorTime = (cursor != null && !cursor.isBlank())
                ? LocalDateTime.parse(cursor)
                : LocalDateTime.now();

        if ("NEWER".equalsIgnoreCase(direction)) {
            allCriteria.add(Criteria.where("createdAt").gt(cursorTime));
            query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
        } else {
            allCriteria.add(Criteria.where("createdAt").lt(cursorTime));
            query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        allCriteria.addAll(getSecurityCriteria(room, currentUserId));
        query.addCriteria(new Criteria().andOperator(allCriteria.toArray(new Criteria[0])));

        query.limit(limit);
        List<Message> messages = mongoTemplate.find(query, Message.class);

        // FE expects DESC order (newest first)
        if ("NEWER".equalsIgnoreCase(direction)) {
            Collections.reverse(messages);
        }

        return buildCursorResponse(messages, limit, false);
    }

    private List<Message> findOlder(Conversation room, LocalDateTime time, int limit, String currentUserId) {
        Query query = new Query();
        List<Criteria> allCriteria = new ArrayList<>();
        allCriteria.add(Criteria.where("conversationId").is(room.getId()));
        allCriteria.add(Criteria.where("createdAt").lt(time));
        allCriteria.addAll(getSecurityCriteria(room, currentUserId));

        query.addCriteria(new Criteria().andOperator(allCriteria.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.limit(limit);

        List<Message> messages = mongoTemplate.find(query, Message.class);
        return messages; // Already DESC
    }

    private List<Message> findNewer(Conversation room, LocalDateTime time, int limit, String currentUserId) {
        Query query = new Query();
        List<Criteria> allCriteria = new ArrayList<>();
        allCriteria.add(Criteria.where("conversationId").is(room.getId()));
        allCriteria.add(Criteria.where("createdAt").gt(time));
        allCriteria.addAll(getSecurityCriteria(room, currentUserId));

        query.addCriteria(new Criteria().andOperator(allCriteria.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
        query.limit(limit);

        List<Message> messages = mongoTemplate.find(query, Message.class);
        Collections.reverse(messages); // Reverse to make it DESC
        return messages;
    }

    private List<Criteria> getSecurityCriteria(Conversation room, String currentUserId) {
        List<Criteria> securityCriteria = new ArrayList<>();

        ConversationMember currentMember = room.getMembers().stream()
                .filter(m -> m.getUserId().equals(currentUserId))
                .findFirst().orElse(null);
        boolean isActive = currentMember != null && isActiveMember(currentMember);

        // Xác định mốc thời gian sớm nhất mà User có quyền xem tin nhắn
        LocalDateTime memberJoinedAt = (currentMember != null && currentMember.getJoinedAt() != null)
                ? currentMember.getJoinedAt()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime deletedBefore = (room.getDeletedBefore() != null)
                ? room.getDeletedBefore().getOrDefault(currentUserId, LocalDateTime.of(1970, 1, 1, 0, 0))
                : LocalDateTime.of(1970, 1, 1, 0, 0);

        LocalDateTime effectiveStartTime = memberJoinedAt.isAfter(deletedBefore) ? memberJoinedAt : deletedBefore;

        securityCriteria.add(Criteria.where("deletedBy").ne(currentUserId));
        securityCriteria.add(Criteria.where("createdAt").gt(effectiveStartTime));

        // Visibility filter: visibleTo is null OR visibleTo contains currentUserId
        securityCriteria.add(new Criteria().orOperator(
                Criteria.where("visibleTo").is(null),
                Criteria.where("visibleTo").size(0),
                Criteria.where("visibleTo").is(currentUserId)
        ));

        if (!isActive) {
            securityCriteria.add(Criteria.where("type").is(MessageType.SYSTEM));
        }

        return securityCriteria;
    }

    private CursorPageResponse<MessageResponse> buildCursorResponse(
            List<Message> messages, int limit, boolean isJump) {

        String baseUrl = s3UtilV2.getS3BaseUrl();
        List<MessageResponse> dtos = messages.stream()
                .map(msg -> messageMapper.mapToMessageResponse(msg, baseUrl))
                .collect(Collectors.toList());
        enrichMessages(dtos);

        // List is DESC (newest at 0, oldest at size-1)
        String newerCursor = messages.isEmpty() ? null : messages.get(0).getCreatedAt().toString();
        String olderCursor = messages.isEmpty() ? null : messages.get(messages.size() - 1).getCreatedAt().toString();

        return CursorPageResponse.<MessageResponse>builder()
                .data(dtos)
                .olderCursor(olderCursor)
                .newerCursor(newerCursor)
                .hasMoreOlder(messages.size() >= limit)
                .hasMoreNewer(isJump || messages.size() >= limit)
                .isJumpResult(isJump)
                .build();
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

        String previewContent = null;
        Map<String, Object> lastMessageMetadata = new HashMap<>();
        
        // Extract attachment metadata for notification rendering (chỉ khi có attachment)
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            Map<String, Object> attachmentMetadata = extractAttachmentMetadata(message.getAttachments());
            lastMessageMetadata.putAll(attachmentMetadata);
        }
        
        if (message.getType() == MessageType.CHAT && message.getContent() != null && !message.getContent().isBlank()) {
            previewContent = message.getContent();
        }
        
        String notificationContentVi = buildNotificationContent(message, "vi");
        String notificationContentEn = buildNotificationContent(message, "en");
        LastMessageInfo lastInfo = LastMessageInfo.builder()
                .messageId(message.getId())
                .senderId(currentUserId)
                .content(previewContent)
                .timestamp(message.getCreatedAt())
                .type(message.getType())
                .status(message.getStatus())
                .metadata(lastMessageMetadata.isEmpty() ? null : lastMessageMetadata)
                .build();

        // 5. Cập nhật lastMessage + tăng unreadCount cho tất cả member trừ sender
        Query query = new Query(Criteria.where("id").is(room.getId()));
        Update update = new Update().set("lastMessage", lastInfo);
        
        if (message.getType() != MessageType.SYSTEM) {
            room.getMembers().stream()
                .filter(this::isActiveMember)
                .filter(m -> !m.getUserId().equals(currentUserId))
                .forEach(m -> update.inc("unreadCounts." + m.getUserId(), 1));
        }

        Conversation updatedRoom = mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class);

        log.info("[Chat] Saved message & updated conversation state for room: {}", room.getId());

        // 6. Push notification qua Kafka cho từng thành viên
        String baseUrl = s3UtilV2.getS3BaseUrl();
        Conversation finalRoom = updatedRoom != null ? updatedRoom : room;

        List<ChatNotification> notifPrototypes = new ArrayList<>(
                List.of(messageMapper.mapToChatNotification(message, baseUrl, 0)));
        enrichNotifications(notifPrototypes);
        ChatNotification baseNotif = notifPrototypes.get(0);

        NotificationType notiType = finalRoom.isGroup() ? NotificationType.MESSAGE_GROUP : NotificationType.MESSAGE_DIRECT;

        finalRoom.getMembers().stream()
            .filter(this::isActiveMember)
            .filter(member -> {
                // Respect visibility if restricted
                if (message.getVisibleTo() != null && !message.getVisibleTo().isEmpty()) {
                    return message.getVisibleTo().contains(member.getUserId());
                }
                return true;
            })
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

                    // Publish RawNotificationEvent for push notifications (only for others)
                    if (!isFromMe) {
                        String dynamicGroupName = finalRoom.getName();
                        if (finalRoom.isGroup() && (dynamicGroupName == null || dynamicGroupName.isBlank())) {
                            // Fetch minimal user info for dynamic name generation
                            Set<String> memberIdsToFetch = finalRoom.getMembers().stream()
                                    .filter(this::isActiveMember)
                                    .map(ConversationMember::getUserId)
                                    .limit(5)
                                    .collect(Collectors.toSet());
                            Map<String, ChatUser> groupUserCache = chatUserRepository.findAllById(memberIdsToFetch).stream()
                                    .collect(Collectors.toMap(ChatUser::getId, u -> u));
                            dynamicGroupName = conversationHelper.getDynamicGroupName(finalRoom, member.getUserId(), groupUserCache);
                        }

                        // Extract attachment metadata for i18n rebuilding
                        Map<String, Object> attachmentMetadata = extractAttachmentMetadata(message.getAttachments());

                        Map<String, Object> payloadMap = new HashMap<>();
                        payloadMap.put("conversationId", finalRoom.getId());
                        payloadMap.put("messageId", message.getId());
                        payloadMap.put("content", notificationContentVi);
                        payloadMap.put("contentVi", notificationContentVi);
                        payloadMap.put("contentEn", notificationContentEn);
                        payloadMap.put("senderId", currentUserId);
                        payloadMap.put("senderName", sender != null ? sender.getFullName() : "Người dùng");
                        payloadMap.put("isGroup", finalRoom.isGroup());
                        payloadMap.put("groupName", dynamicGroupName != null ? dynamicGroupName : "");
                        payloadMap.put("conversationAvatar", finalRoom.isGroup() ? (finalRoom.getAvatar() != null ? baseUrl + finalRoom.getAvatar() : "") : (sender != null && sender.getAvatar() != null ? baseUrl + sender.getAvatar() : ""));
                        payloadMap.put("messageType", message.getType().name());
                        payloadMap.putAll(attachmentMetadata);

                        RawNotificationEvent rawEvent = RawNotificationEvent.builder()
                                .recipientId(member.getUserId())
                                .actorId(currentUserId)
                                .actorName(sender != null ? sender.getFullName() : "Người dùng")
                                .actorAvatar(sender != null && sender.getAvatar() != null ? baseUrl + sender.getAvatar() : null)
                                .type(notiType)
                                .referenceId(message.getId())
                                .payload(payloadMap)
                                .occurredAt(message.getCreatedAt())
                                .build();

                        rawNotificationEventPublisher.publish(rawEvent);
                    }
                });

        // 7. Ingest vào AI system
        log.info("[Kafka] Sending user message to 'message-created' for AI ingestion.");
        kafkaTemplate.send("message-created", AiMessageSaveEvent.builder()
                .userId(currentUserId)
                .chatId(room.getId())
                .content(message.getContent())
                .build());

        messageIndexEventPublisher.publishIndexRequest(message);
        chatInteractionEventPublisher.publishDirectChatInteraction(message, finalRoom);
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
        messageIndexEventPublisher.publishIndexRequest(message);
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

        String baseUrl = s3UtilV2.getS3BaseUrl();
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

    @Override
    public MessageResponse findById(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        Conversation room = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        assertConversationMember(room, currentUserId);

        String baseUrl = s3UtilV2.getS3BaseUrl();
        MessageResponse dto = messageMapper.mapToMessageResponse(message, baseUrl);
        List<MessageResponse> dtos = new ArrayList<>(List.of(dto));
        enrichMessages(dtos);
        return dtos.get(0);
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    /**
     * Kiểm tra user có trong members của conversation không.
     * Nếu không → throw UNAUTHORIZED (403).
     */
    @Override
    public List<MessageResponse> getMessagesSince(String conversationId, String sinceId, String userId) {
        // 1. Kiểm tra quyền membership
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId) && isActiveMember(m));
        
        if (!isMember) {
            throw new AppException(ErrorCode.CHAT_NOT_A_MEMBER);
        }

        // 2. Lấy 100 tin nhắn gần nhất tính từ sinceId
        List<Message> messages = messageRepository.findTop100ByConversationIdAndIdGreaterThanAndStatusNot(
                conversationId, sinceId, MessageStatus.REVOKED);

        String baseUrl = s3UtilV2.getS3BaseUrl();
        List<MessageResponse> dtos = messages.stream()
                .map(msg -> messageMapper.mapToMessageResponse(msg, baseUrl))
                .collect(Collectors.toList());

        enrichMessages(dtos);
        return dtos;
    }

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
        String baseUrl = s3UtilV2.getS3BaseUrl();

        for (int i = 0; i < dtos.size(); i++) {
            MessageResponse d = dtos.get(i);
            MessageResponse enriched = d;

            if (d.type() == MessageType.SYSTEM && d.metadata() != null) {
                enriched = enriched.withMetadata(enrichSystemMetadata(d.metadata(), baseUrl));
            }

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
        String baseUrl = s3UtilV2.getS3BaseUrl();

        for (int i = 0; i < notifs.size(); i++) {
            ChatNotification n = notifs.get(i);
            ChatNotification enriched = n;

            if (n.type() == MessageType.SYSTEM && n.metadata() != null) {
                enriched = enriched.toBuilder()
                        .metadata(enrichSystemMetadata(n.metadata(), baseUrl))
                        .build();
            }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichSystemMetadata(Map<String, Object> metadata, String baseUrl) {
        if (metadata == null) return null;
        Map<String, Object> newMetadata = new HashMap<>(metadata);

        if (newMetadata.get("targetAvatar") instanceof String avatar && !avatar.isBlank() && !avatar.startsWith("http")) {
            newMetadata.put("targetAvatar", baseUrl + avatar);
        }

        if (newMetadata.get("payload") instanceof Map<?, ?> payload) {
            Map<String, Object> newPayload = new HashMap<>((Map<String, Object>) payload);
            if (newPayload.get("targetAvatars") instanceof List<?> avatars) {
                List<String> enrichedAvatars = avatars.stream()
                        .map(obj -> {
                            if (obj instanceof String avatar && !avatar.isBlank() && !avatar.startsWith("http")) {
                                return baseUrl + avatar;
                            }
                            return (String) obj;
                        })
                        .toList();
                newPayload.put("targetAvatars", enrichedAvatars);
            }
            newMetadata.put("payload", newPayload);
        }

        return newMetadata;
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

            String baseUrl = s3UtilV2.getS3BaseUrl();
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
                .map(a -> {
                    String originalFileName = a.originalFileName();
                    String extension = null;
                    if (originalFileName != null && originalFileName.contains(".")) {
                        extension = originalFileName.substring(originalFileName.lastIndexOf(".") + 1).toLowerCase().trim();
                    }
                    return AttachmentInfo.builder()
                            .key(a.key())
                            .url(a.url())
                            .fileName(a.fileName())
                            .originalFileName(originalFileName)
                            .extension(extension)
                            .contentType(a.contentType())
                            .size(a.size())
                            .build();
                })
                .toList();
    }

    private String buildPreviewContent(Message message) {
        String attachmentPreview = buildAttachmentPreview(message, "vi");
        if (attachmentPreview != null) {
            return attachmentPreview;
        }

        return switch (message.getType() == null ? MessageType.CHAT : message.getType()) {
            case IMAGE -> "[Hình ảnh]";
            case VIDEO -> "[Video]";
            case FILE -> "[Tệp]";
            case LINK -> "[Liên kết]";
            default -> message.getContent();
        };
    }

    private String buildNotificationContent(Message message, String locale) {
        String attachmentPreview = buildAttachmentPreview(message, locale);
        if (attachmentPreview != null) {
            return attachmentPreview;
        }

        String content = message.getContent();
        if (content != null && !content.isBlank()) {
            return content;
        }

        return isEnglish(locale) ? "Sent a message" : "\u0110\u00e3 g\u1eedi m\u1ed9t tin nh\u1eafn";
    }

    private String buildAttachmentPreview(Message message, String locale) {
        List<AttachmentInfo> attachments = message.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        int imageCount = 0;
        int videoCount = 0;
        for (AttachmentInfo attachment : attachments) {
            String contentType = attachment.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                imageCount++;
            } else if (contentType != null && contentType.startsWith("video/")) {
                videoCount++;
            }
        }

        int mediaCount = imageCount + videoCount;
        if (mediaCount == attachments.size() && mediaCount > 0) {
            return buildMediaPreview(imageCount, videoCount, locale);
        }

        AttachmentInfo firstFile = attachments.stream()
                .filter(a -> {
                    String contentType = a.getContentType();
                    return contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"));
                })
                .findFirst()
                .orElse(attachments.get(0));

        return "[File] " + resolveAttachmentFileName(firstFile);
    }

    private String buildMediaPreview(int imageCount, int videoCount, String locale) {
        boolean english = isEnglish(locale);

        if (imageCount > 0 && videoCount > 0) {
            String imageLabel = imageCount > 1
                    ? (english ? "Photos" : "Nhiều ảnh")
                    : (english ? "Photo" : "Ảnh");
            String videoLabel = videoCount > 1
                    ? (english ? "Videos" : "Nhiều video")
                    : (english ? "Video" : "Video");
            return "[" + imageLabel + (english ? " and " : " và ") + videoLabel + "]";
        }

        if (imageCount > 0) {
            return imageCount > 1
                    ? (english ? "[Photos]" : "[Nhiều ảnh]")
                    : (english ? "[Photo]" : "[Ảnh]");
        }

        if (videoCount > 0) {
            return videoCount > 1
                    ? (english ? "[Videos]" : "[Nhiều video]")
                    : (english ? "[Video]" : "[Video]");
        }

        return "[Other]";
    }

    private String resolveAttachmentFileName(AttachmentInfo attachment) {
        if (attachment == null) {
            return "file";
        }
        if (attachment.getOriginalFileName() != null && !attachment.getOriginalFileName().isBlank()) {
            return attachment.getOriginalFileName();
        }
        if (attachment.getFileName() != null && !attachment.getFileName().isBlank()) {
            return attachment.getFileName();
        }
        return "file";
    }

    private boolean isEnglish(String locale) {
        return "en".equalsIgnoreCase(locale);
    }

    private Map<String, Object> extractAttachmentMetadata(List<AttachmentInfo> attachments) {
        Map<String, Object> metadata = new HashMap<>();
        if (attachments == null || attachments.isEmpty()) {
            metadata.put("imageCount", 0);
            metadata.put("videoCount", 0);
            log.debug("[Attachment Debug] Attachments is null or empty");
            return metadata;
        }

        int imageCount = 0;
        int videoCount = 0;
        for (AttachmentInfo attachment : attachments) {
            String contentType = attachment.getContentType();
            log.debug("[Attachment Debug] Processing attachment: fileName={}, contentType={}", 
                    attachment.getFileName(), contentType);
            if (contentType != null && contentType.startsWith("image/")) {
                imageCount++;
            } else if (contentType != null && contentType.startsWith("video/")) {
                videoCount++;
            }
        }

        log.debug("[Attachment Debug] Final counts: imageCount={}, videoCount={}, total attachments={}", 
                imageCount, videoCount, attachments.size());
        metadata.put("imageCount", imageCount);
        metadata.put("videoCount", videoCount);
        return metadata;
    }

}
