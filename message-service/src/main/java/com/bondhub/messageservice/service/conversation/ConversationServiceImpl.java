package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.repository.ChatRoomRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.dto.response.ConversationMemberResponse;
import com.bondhub.messageservice.dto.response.ReadReceiptNotification;
import com.bondhub.messageservice.model.enums.MemberRole;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bondhub.common.dto.PageResponse;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatUserRepository chatUserRepository;
    private final SecurityUtil securityUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    private String generateChatRoomId(String senderId, String recipientId) {
        return (senderId.compareTo(recipientId) < 0)
                ? String.format("%s_%s", senderId, recipientId)
                : String.format("%s_%s", recipientId, senderId);
    }

    @Override
    public Optional<String> getChatRoomId(
            String senderId,
            String recipientId,
            boolean createNewRoomIfNotExists) {
        String chatId = generateChatRoomId(senderId, recipientId);

        return chatRoomRepository
                .findByChatId(chatId)
                .map(Conversation::getChatId)
                .or(() -> {
                    if (createNewRoomIfNotExists) {
                        Conversation conversation = Conversation
                                .builder()
                                .chatId(chatId)
                                .senderId(senderId)
                                .recipientId(recipientId)
                                .members(new HashSet<>(Arrays.asList(
                                    ConversationMember.builder().userId(senderId).role(MemberRole.OWNER).joinedAt(LocalDateTime.now()).build(),
                                    ConversationMember.builder().userId(recipientId).role(MemberRole.MEMBER).joinedAt(LocalDateTime.now()).build()
                                )))
                                .build();

                        chatRoomRepository.save(conversation);

                        return Optional.of(chatId);
                    }

                    return Optional.empty();
                });
    }

    @Override
    public Conversation createInitialChatRoom(String userA, String userB, LocalDateTime timestamp) {
        String chatId = generateChatRoomId(userA, userB);

        return chatRoomRepository.findByChatId(chatId).orElseGet(() -> {
            Conversation newRoom = Conversation.builder()
                    .chatId(chatId)
                    .senderId(userA)
                    .recipientId(userB)
                    .lastMessage(null) // Empty message to trigger UI greeting
                    .lastMessageTime(timestamp) // Allows sorting in inbox
                    .members(new HashSet<>(Arrays.asList(
                        ConversationMember.builder().userId(userA).role(MemberRole.OWNER).joinedAt(timestamp).build(),
                        ConversationMember.builder().userId(userB).role(MemberRole.MEMBER).joinedAt(timestamp).build()
                    )))
                    .build();
            log.info("Created initial chat room proactively for: {}", chatId);
            return chatRoomRepository.save(newRoom);
        });
    }

    @Override
    public ConversationResponse getConversationForUser(String userId, String partnerId) {
        String chatId = generateChatRoomId(userId, partnerId);
        Conversation room = chatRoomRepository.findByChatId(chatId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        ChatUser partner = chatUserRepository.findById(partnerId)
                .orElseGet(() -> ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());

        ChatUser currentUser = chatUserRepository.findById(userId).orElse(null);
        boolean viewerCanSee = currentUser != null && currentUser.isShowSeenStatus();

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        return getConversationResponse(userId, viewerCanSee, baseUrl, room, partnerId, partner);
    }

    @Override
    public PageResponse<List<ConversationResponse>> getUserConversations(int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessageTime"));
        Page<Conversation> roomsPage = chatRoomRepository.findAllRoomsByUserId(currentUserId, pageable);

        if (roomsPage.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        // 1. Lấy tất cả partnerId
        Set<String> allPartnerIds = roomsPage.getContent().stream()
                .map(room -> room.getSenderId().equals(currentUserId) ? room.getRecipientId()
                        : room.getSenderId())
                .collect(Collectors.toSet());

        // 2. Query batch từ Mirror DB
        List<ChatUser> partners = chatUserRepository.findAllById(allPartnerIds);
        Map<String, ChatUser> partnerMap = partners.stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
        boolean viewerCanSee = currentUser != null && currentUser.isShowSeenStatus();

        // 3. Tìm những ID bị thiếu để bắn Event (Chỉ bắn 1 lần cho mỗi ID)
        allPartnerIds.stream()
                .filter(id -> !partnerMap.containsKey(id))
                .forEach(id -> eventPublisher.publishEvent(new UserSyncEvent(id)));

        // 4. Map sang Response
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        return PageResponse.fromPage(roomsPage, room -> {
            String partnerId = room.getSenderId().equals(currentUserId) ? room.getRecipientId()
                    : room.getSenderId();
            ChatUser partner = partnerMap.getOrDefault(partnerId,
                    ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());

            return getConversationResponse(currentUserId, viewerCanSee, baseUrl, room, partnerId, partner);
        });
    }

    private ConversationResponse getConversationResponse(String currentUserId, boolean viewerCanSee, String baseUrl, Conversation room, String partnerId, ChatUser partner) {
        List<ConversationMemberResponse> members = buildConversationMembers(room, currentUserId, baseUrl, viewerCanSee);

        return ConversationResponse.builder()
                .chatId(room.getChatId())
                .partnerId(partnerId)
                .partnerName(partner.getFullName())
                .partnerAvatar(partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null)
                .partnerStatus(partner.getStatus())
                .lastSeenAt(partner.getLastUpdatedAt())
                .lastMessage(room.getLastMessage())
                .lastMessageId(room.getLastMessageId())
                .lastMessageTime(room.getLastMessageTime())
                .unreadCount(
                        room.getUnreadCounts() != null ? room.getUnreadCounts().getOrDefault(currentUserId, 0) : 0)
                .members(members)
                .build();
    }

    @Override
    public void markAsRead(String chatId) {
        String currentUserId = securityUtil.getCurrentUserId();

        // Tìm room để lấy lastMessageId mới nhất trong DB
        Conversation room = chatRoomRepository.findByChatId(chatId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        String finalReadId = room.getLastMessageId();

        // Query tìm đúng Conversation và đúng Member trong mảng members
        Query query = new Query(Criteria.where("chatId").is(chatId)
                .and("members.userId").is(currentUserId));

        // Cập nhật: reset unread về 0 và set mốc đọc mới bằng tin nhắn cuối cùng của phòng
        Update update = new Update()
                .set("unreadCounts." + currentUserId, 0);

        if (finalReadId != null) {
            update.set("members.$.lastReadMessageId", finalReadId);
        }

        UpdateResult result = mongoTemplate.updateFirst(query, update, Conversation.class);

        if (result.getModifiedCount() > 0) {
            chatUserRepository.findById(currentUserId).ifPresent(user -> {
                if (user.isShowSeenStatus()) {
                    broadcastReadReceipt(chatId, currentUserId, finalReadId);
                }
            });
        }
    }

    private void broadcastReadReceipt(String chatId, String userId, String lastReadMessageId) {
        chatRoomRepository.findByChatId(chatId).ifPresent(room -> {
            String partnerId = room.getSenderId().equals(userId) ? room.getRecipientId() : room.getSenderId();

            chatUserRepository.findById(partnerId).ifPresent(partner -> {
                if (partner.isShowSeenStatus()) {
                    messagingTemplate.convertAndSendToUser(partnerId, "/queue/read-receipts", ReadReceiptNotification.builder()
                            .chatId(chatId)
                            .userId(userId)
                            .lastReadMessageId(lastReadMessageId)
                            .build());
                }
            });
        });
    }

    private List<ConversationMemberResponse> buildConversationMembers(
            Conversation room,
            String currentUserId,
            String baseUrl,
            boolean viewerCanSee) {

        return room.getMembers().stream()
                .filter(m -> !m.getUserId().equals(currentUserId)) // Không hiện chính mình "đã xem" cho mình
                .map(m -> {
                    ChatUser memberInfo = chatUserRepository.findById(m.getUserId()).orElse(null);

                    // QUY TẮC CÔNG BẰNG:
                    // 1. Người xem (viewerCanSee) phải bật
                    // 2. Chủ thể (memberInfo.isShowSeenStatus) phải bật
                    boolean canSeeStatus = viewerCanSee
                            && memberInfo != null
                            && memberInfo.isShowSeenStatus();

                    return ConversationMemberResponse.builder()
                            .userId(m.getUserId())
                            .fullName(memberInfo != null ? memberInfo.getFullName() : "Người dùng")
                            .avatar(memberInfo != null && memberInfo.getAvatar() != null ? baseUrl + memberInfo.getAvatar() : null)
                            .lastReadMessageId(canSeeStatus ? m.getLastReadMessageId() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
