package com.bondhub.messageservice.service;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.client.UserServiceClient;
import com.bondhub.messageservice.dto.response.ChatNotification;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.enums.MessageType;
import com.bondhub.messageservice.repository.ChatMessageRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.MessageResponse;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatUserRepository chatUserRepository;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityUtil securityUtil;
    private final UserServiceClient userServiceClient;
    private final MongoTemplate mongoTemplate;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    public Message save(Message message) {
        var chatId = chatRoomService
                .getChatRoomId(message.getSenderId(), message.getRecipientId(), true)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        message.setChatId(chatId);

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

    public PageResponse<List<MessageResponse>> findChatMessages(String recipientId, int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        var chatId = chatRoomService.getChatRoomId(currentUserId, recipientId, false);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (chatId.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        Page<Message> messagePage = chatMessageRepository.findByChatId(chatId.get(), pageable);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return PageResponse.fromPage(messagePage, msg -> MessageResponse.builder()
                .id(msg.getId())
                .chatId(msg.getChatId())
                .senderId(msg.getSenderId())
                .senderName(msg.getSenderName())
                .senderAvatar(msg.getSenderAvatar() != null ? baseUrl + msg.getSenderAvatar() : null)
                .recipientId(msg.getRecipientId())
                .content(msg.getContent())
                .clientMessageId(msg.getClientMessageId())
                .type(msg.getType())
                .createdAt(msg.getCreatedAt())
                .lastModifiedAt(msg.getLastModifiedAt())
                .build());
    }

    public void sendMessage(Message message) {
        Message savedMsg = save(message);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        String previewContent = switch (savedMsg.getType() == null ? MessageType.CHAT
                : savedMsg.getType()) {
            case IMAGE -> "[Hình ảnh]";
            case FILE -> "[Tệp tin]";
            default -> savedMsg.getContent();
        };

        Query query = new Query(Criteria.where("chatId").is(savedMsg.getChatId()));
        Update update = new Update()
                .set("lastMessage", previewContent)
                .set("lastMessageTime", savedMsg.getCreatedAt());
        mongoTemplate.updateFirst(query, update, Conversation.class);

        log.info("[Chat] Sending real-time message to: {}", savedMsg.getRecipientId());

        messagingTemplate.convertAndSendToUser(
                message.getRecipientId(),
                "/queue/messages",
                ChatNotification.builder()
                        .id(savedMsg.getId())
                        .chatId(savedMsg.getChatId())
                        .senderId(savedMsg.getSenderId())
                        .senderName(savedMsg.getSenderName())
                        .senderAvatar(savedMsg.getSenderAvatar() != null ? baseUrl + savedMsg.getSenderAvatar() : null)
                        .recipientId(savedMsg.getRecipientId())
                        .content(savedMsg.getContent())
                        .clientMessageId(savedMsg.getClientMessageId())
                        .timestamp(savedMsg.getCreatedAt())
                        .build());

        if (!message.getSenderId().equals(message.getRecipientId())) {
            messagingTemplate.convertAndSendToUser(
                    message.getSenderId(),
                    "/queue/messages",
                    ChatNotification.builder()
                            .id(savedMsg.getId())
                            .chatId(savedMsg.getChatId())
                            .senderId(savedMsg.getSenderId())
                            .senderName(savedMsg.getSenderName())
                            .senderAvatar(
                                    savedMsg.getSenderAvatar() != null ? baseUrl + savedMsg.getSenderAvatar() : null)
                            .recipientId(savedMsg.getRecipientId())
                            .content(savedMsg.getContent())
                            .clientMessageId(savedMsg.getClientMessageId())
                            .timestamp(savedMsg.getCreatedAt())
                            .build());
        }
    }
}
