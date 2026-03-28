package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.client.UserServiceClient;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.dto.response.ReplyMetadataResponse;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.messageservice.model.enums.MessageType;
import com.bondhub.messageservice.repository.ChatMessageRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;

import com.bondhub.messageservice.mapper.MessageMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatUserRepository chatUserRepository;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityUtil securityUtil;
    private final UserServiceClient userServiceClient;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Override
    public Message save(Message message) {
        var room = conversationService
                .getDirectConversation(message.getSenderId(), message.getRecipientId(), true)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        message.setChatId(room.getChatId());

        // Lookup sender info for snapshot (Cold Start if missing)
        ChatUser sender = chatUserRepository.findById(message.getSenderId())
                .orElseGet(() -> fetchAndSaveUserFromUserService(message.getSenderId()));

        message.setSenderName(sender.getFullName());
        message.setSenderAvatar(sender.getAvatar());

        chatMessageRepository.save(message);
        return message;
    }

    private ChatUser fetchAndSaveUserFromUserService(String userId) {

        log.info("Cold Start: Fetching user {} from user-service", userId);
        UserSummaryResponse userDto = userServiceClient.getUserById(userId).data();
        ChatUser mirrorUser = ChatUser.builder()
                .id(userDto.id())
                .fullName(userDto.fullName())
                .avatar(userDto.avatar())
                .lastUpdatedAt(LocalDateTime.now())
                .build();
        return chatUserRepository.save(mirrorUser);
    }

    @Override
    public PageResponse<List<MessageResponse>> findChatMessages(String recipientId, int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        var roomOpt = conversationService.getDirectConversation(currentUserId, recipientId, false);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (roomOpt.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        Page<Message> messagePage = chatMessageRepository.findByChatIdAndNotDeleted(roomOpt.get().getChatId(),
                currentUserId, pageable);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        List<MessageResponse> dtos = messagePage.getContent().stream()
                .map(msg -> messageMapper.mapToMessageResponse(msg, baseUrl))
                .collect(Collectors.toList());

        enrichMessages(dtos);

        return PageResponse.fromPageData(messagePage, dtos);
    }

    private void enrichMessages(List<MessageResponse> dtos) {
        Set<String> userIds = new HashSet<>();
        dtos.forEach(d -> {
            userIds.add(d.senderId());
            if (d.replyTo() != null) {
                userIds.add(d.replyTo().senderId());
            }
        });

        if (userIds.isEmpty())
            return;

        Map<String, ChatUser> userMap = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        // Note: record fields are accessed via methodName() not getMethodName()
        for (int i = 0; i < dtos.size(); i++) {
            MessageResponse d = dtos.get(i);
            MessageResponse enriched = d;

            ChatUser sender = userMap.get(d.senderId());
            if (sender != null) {
                enriched = enriched.withSenderName(sender.getFullName())
                        .withSenderAvatar(sender.getAvatar() != null
                                ? S3Util.getS3BaseUrl(bucketName, region) + sender.getAvatar()
                                : null);
            }

            if (d.replyTo() != null) {
                ChatUser replySender = userMap.get(d.replyTo().senderId());
                if (replySender != null) {
                    ReplyMetadataResponse enrichedReply = d.replyTo().withSenderName(replySender.getFullName());
                    enriched = enriched.withReplyTo(enrichedReply);
                }
            }
            dtos.set(i, enriched);
        }
    }

    @Override
    public void sendMessage(Message message) {
        Message savedMsg = save(message);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        String previewContent = switch (savedMsg.getType() == null ? MessageType.CHAT
                : savedMsg.getType()) {
            case IMAGE -> "[IMAGE]";
            case FILE -> "[FILE]";
            default -> savedMsg.getContent();
        };

        LastMessageInfo lastInfo = LastMessageInfo.builder()
                .messageId(savedMsg.getId())
                .senderId(savedMsg.getSenderId())
                .content(previewContent)
                .timestamp(savedMsg.getCreatedAt())
                .type(savedMsg.getType())
                .status(savedMsg.getStatus())
                .build();

        Query query = new Query(Criteria.where("chatId").is(savedMsg.getChatId()));
        Update update = new Update()
                .set("lastMessage", lastInfo)
                .inc("unreadCounts." + savedMsg.getRecipientId(), 1);

        Conversation updatedRoom = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class);

        Integer recipientUnreadCount = updatedRoom != null
                ? updatedRoom.getUnreadCounts().getOrDefault(savedMsg.getRecipientId(), 0)
                : 1;
        Integer senderUnreadCount = updatedRoom != null
                ? updatedRoom.getUnreadCounts().getOrDefault(savedMsg.getSenderId(), 0)
                : 0;

        log.info("[Chat] Sending real-time message to: {}", savedMsg.getRecipientId());

        ChatNotification notification = messageMapper.mapToChatNotification(savedMsg, baseUrl, recipientUnreadCount);

        // Enrich notification before sending
        List<ChatNotification> notifList = new ArrayList<>(List.of(notification));
        enrichNotifications(notifList);
        notification = notifList.get(0);

        // For recipient: isFromMe = false
        notification = notification.toBuilder().isFromMe(false).build();

        messagingTemplate.convertAndSendToUser(
                message.getRecipientId(),
                "/queue/messages",
                notification);

        if (!message.getSenderId().equals(message.getRecipientId())) {
            ChatNotification senderNotif = messageMapper.mapToChatNotification(savedMsg, baseUrl, senderUnreadCount);

            List<ChatNotification> senderNotifList = new ArrayList<>(List.of(senderNotif));
            enrichNotifications(senderNotifList);
            senderNotif = senderNotifList.get(0);

            // For sender: isFromMe = true (sync self devices)
            senderNotif = senderNotif.toBuilder().isFromMe(true).build();

            messagingTemplate.convertAndSendToUser(
                    message.getSenderId(),
                    "/queue/messages",
                    senderNotif);
        }
    }

    private void enrichNotifications(List<ChatNotification> notifs) {
        Set<String> userIds = new HashSet<>();
        notifs.forEach(n -> {
            userIds.add(n.senderId());
            if (n.replyTo() != null) {
                userIds.add(n.replyTo().senderId());
            }
        });

        if (userIds.isEmpty())
            return;

        Map<String, ChatUser> userMap = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        for (int i = 0; i < notifs.size(); i++) {
            ChatNotification n = notifs.get(i);
            ChatNotification enriched = n;

            ChatUser sender = userMap.get(n.senderId());
            if (sender != null) {
                enriched = enriched.withSenderName(sender.getFullName())
                        .withSenderAvatar(sender.getAvatar() != null
                                ? S3Util.getS3BaseUrl(bucketName, region) + sender.getAvatar()
                                : null);
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

    @Override
    public void revokeMessage(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Message message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getSenderId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        message.setStatus(MessageStatus.REVOKED);
        message.setContent(null);
        message.setReplyTo(null);
        chatMessageRepository.save(message);

        updateLastMessageIfRevoked(message);
        broadcastStatusChange(message.getChatId(), messageId, MessageStatus.REVOKED);
    }

    @Override
    public void deleteMessageForMe(String messageId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Query query = new Query(Criteria.where("id").is(messageId));
        Update update = new Update().addToSet("deletedBy", currentUserId);
        mongoTemplate.updateFirst(query, update, Message.class);
    }

    private void updateLastMessageIfRevoked(Message revokedMsg) {
        Query query = new Query(Criteria.where("chatId").is(revokedMsg.getChatId())
                .and("lastMessage.messageId").is(revokedMsg.getId()));
        Update update = new Update()
                .set("lastMessage.content", "Tin nhắn đã được thu hồi")
                .set("lastMessage.status", com.bondhub.common.enums.MessageStatus.REVOKED);
        mongoTemplate.updateFirst(query, update, Conversation.class);
    }

    private void broadcastStatusChange(String chatId, String messageId, MessageStatus newStatus) {
        Conversation conversation = mongoTemplate.findOne(
                new Query(Criteria.where("chatId").is(chatId)),
                Conversation.class);

        if (conversation == null)
            return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MESSAGE_STATUS_UPDATE");
        payload.put("chatId", chatId);
        payload.put("messageId", messageId);
        payload.put("newStatus", newStatus);

        for (ConversationMember member : conversation.getMembers()) {
            // For the recipient in frontend to update local state,
            // we need to provide the 'partnerId' from their perspective.
            Map<String, Object> personalPayload = new HashMap<>(payload);
            String partnerId = conversation.isGroup() ? chatId
                    : (member.getUserId().equals(conversation.getSenderId()) ? conversation.getRecipientId()
                    : conversation.getSenderId());
            personalPayload.put("partnerId", partnerId);

            messagingTemplate.convertAndSendToUser(
                    member.getUserId(),
                    "/queue/status-updates",
                    personalPayload);
        }
    }
}
