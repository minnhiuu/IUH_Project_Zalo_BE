package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.repository.ChatRoomRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.dto.response.ConversationMemberResponse;
import com.bondhub.messageservice.dto.response.ReadReceiptNotification;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.common.enums.MessageType;
import com.bondhub.common.dto.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    private String generateConversationId(String senderId, String recipientId) {
        return (senderId.compareTo(recipientId) < 0)
                ? String.format("%s_%s", senderId, recipientId)
                : String.format("%s_%s", recipientId, senderId);
    }

    @Override
    public Optional<Conversation> getDirectConversation(
            String senderId,
            String recipientId,
            boolean createNewRoomIfNotExists) {
        String conversationId = generateConversationId(senderId, recipientId);

        if (createNewRoomIfNotExists) {
            return Optional.of(createInitialChatRoom(senderId, recipientId, LocalDateTime.now()));
        }

        return chatRoomRepository.findByConversationId(conversationId);
    }

    @Override
    public Conversation createInitialChatRoom(String userA, String userB, LocalDateTime timestamp) {
        String conversationId = generateConversationId(userA, userB);

        return chatRoomRepository.findByConversationId(conversationId).orElseGet(() -> {
            Conversation newRoom = Conversation.builder()
                    .conversationId(conversationId)
                    .senderId(userA)
                    .recipientId(userB)
                    .lastMessage(LastMessageInfo.builder()
                            .timestamp(timestamp)
                            .build()) // Set timestamp in embedded object for sorting
                    .members(new HashSet<>(Arrays.asList(
                            ConversationMember.builder().userId(userA).role(MemberRole.OWNER).joinedAt(timestamp)
                                    .build(),
                            ConversationMember.builder().userId(userB).role(MemberRole.MEMBER).joinedAt(timestamp)
                                    .build())))
                    .build();
            log.info("Created initial chat room proactively for: {}", conversationId);
            return chatRoomRepository.save(newRoom);
        });
    }

    @Override
    public ConversationResponse getConversationForUser(String userId, String partnerId) {
        String conversationId = generateConversationId(userId, partnerId);
        Conversation room = chatRoomRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // Gom ID thành viên và partner để fetch 1 lần
        Set<String> userIds = new HashSet<>();
        userIds.add(userId);
        userIds.add(partnerId);
        room.getMembers().forEach(m -> userIds.add(m.getUserId()));

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        ChatUser partner = userCache.getOrDefault(partnerId,
                ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());

        // Ghi đè tên hiển thị cho trường hợp chat với chính mình (My Documents)
        if (partnerId.equals(userId)) {
            partner = ChatUser.builder().id(partnerId).fullName("My Documents").avatar("cloud.png")
                    .showSeenStatus(false).build();
        }

        ChatUser currentUser = userCache.get(userId);
        boolean viewerCanSee = currentUser != null && currentUser.isShowSeenStatus();
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return buildConversationResponse(room, partner, userId, userCache, baseUrl, viewerCanSee);
    }

    public PageResponse<List<ConversationResponse>> getUserConversations(int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessage.timestamp"));
        Page<Conversation> roomsPage = chatRoomRepository.findAllRoomsByUserId(currentUserId, pageable);

        if (roomsPage.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        // 1. Gom TẤT CẢ các ID cần thiết: Partner IDs + Member IDs trong các room +
        // Current User
        Set<String> allUserIdsToFetch = new HashSet<>();
        allUserIdsToFetch.add(currentUserId);
        roomsPage.getContent().forEach(room -> {
            allUserIdsToFetch.add(room.getSenderId());
            allUserIdsToFetch.add(room.getRecipientId());
            room.getMembers().forEach(m -> allUserIdsToFetch.add(m.getUserId()));
        });

        // 2. Fetch 1 lần duy nhất toàn bộ thông tin User
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allUserIdsToFetch).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        ChatUser currentUser = userCache.get(currentUserId);
        boolean viewerCanSee = currentUser != null && currentUser.isShowSeenStatus();
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        // 3. Map sang Response
        return PageResponse.fromPage(roomsPage, room -> {
            String partnerId = room.getSenderId().equals(currentUserId) ? room.getRecipientId() : room.getSenderId();
            ChatUser partner = userCache.getOrDefault(partnerId,
                    ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());

            // Ghi đè tên hiển thị cho trường hợp chat với chính mình (My Documents)
            if (partnerId.equals(currentUserId)) {
                partner = ChatUser.builder().id(partnerId).fullName("My Documents").avatar("cloud.png")
                        .showSeenStatus(false).build();
            }

            // Sync user if not found in cache (skip for our special hardcoded partners)
            if (!userCache.containsKey(partnerId) && !partnerId.equals(currentUserId)
                    && !partnerId.equals("ai-assistant-001")) {
                eventPublisher.publishEvent(new UserSyncEvent(partnerId));
            }

            return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee);
        });
    }

    private ConversationResponse buildConversationResponse(
            Conversation room, ChatUser partner, String currentUserId,
            Map<String, ChatUser> userCache, String baseUrl, boolean viewerCanSee) {

        LastMessageInfo last = room.getLastMessage();
        List<ConversationMemberResponse> members = buildMembersWithCache(room, currentUserId, userCache, baseUrl,
                viewerCanSee);

        return ConversationResponse.builder()
                .conversationId(room.getConversationId())
                .partnerId(partner.getId())
                .partnerName(partner.getFullName())
                .partnerAvatar(partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null)
                .partnerStatus(partner.getStatus())
                .lastSeenAt(partner.getLastUpdatedAt())
                .lastMessage(last != null ? last.getContent() : "")
                .lastMessageId(last != null ? last.getMessageId() : null)
                .lastMessageTime(last != null ? last.getTimestamp() : null)
                .isLastMessageFromMe(
                        last != null && last.getSenderId() != null && last.getSenderId().equals(currentUserId))
                .lastMessageType(last != null ? last.getType() : MessageType.CHAT)
                .unreadCount(room.getUnreadCounts() != null ? room.getUnreadCounts().getOrDefault(currentUserId, 0) : 0)
                .lastMessageStatus(last != null ? last.getStatus() : null)
                .members(members)
                .build();
    }

    private List<ConversationMemberResponse> buildMembersWithCache(
            Conversation room, String currentUserId, Map<String, ChatUser> userCache, String baseUrl,
            boolean viewerCanSee) {

        return room.getMembers().stream()
                .filter(m -> !m.getUserId().equals(currentUserId))
                .map(m -> {
                    ChatUser memberInfo = userCache.get(m.getUserId());
                    boolean canSeeStatus = viewerCanSee && memberInfo != null && memberInfo.isShowSeenStatus();

                    return ConversationMemberResponse.builder()
                            .userId(m.getUserId())
                            .fullName(memberInfo != null ? memberInfo.getFullName() : "Người dùng")
                            .avatar(memberInfo != null && memberInfo.getAvatar() != null
                                    ? baseUrl + memberInfo.getAvatar()
                                    : null)
                            .lastReadMessageId(canSeeStatus ? m.getLastReadMessageId() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public void markAsRead(String conversationId) {
        String currentUserId = securityUtil.getCurrentUserId();

        // Tìm room để lấy lastMessageId mới nhất trong DB
        Conversation room = chatRoomRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        String finalReadId = room.getLastMessage() != null ? room.getLastMessage().getMessageId() : null;

        // Query tìm đúng Conversation và đúng Member trong mảng members
        Query query = new Query(Criteria.where("conversationId").is(conversationId)
                .and("members.userId").is(currentUserId));

        // Cập nhật: reset unread về 0 và set mốc đọc mới bằng tin nhắn cuối cùng của
        // phòng
        Update update = new Update()
                .set("unreadCounts." + currentUserId, 0);

        if (finalReadId != null) {
            update.set("members.$.lastReadMessageId", finalReadId);
        }

        UpdateResult result = mongoTemplate.updateFirst(query, update, Conversation.class);

        if (result.getModifiedCount() > 0) {
            chatUserRepository.findById(currentUserId).ifPresent(user -> {
                if (user.isShowSeenStatus()) {
                    broadcastReadReceipt(conversationId, currentUserId, finalReadId);
                }
            });
        }
    }

    private void broadcastReadReceipt(String conversationId, String userId, String lastReadMessageId) {
        chatRoomRepository.findByConversationId(conversationId).ifPresent(room -> {
            String partnerId = room.getSenderId().equals(userId) ? room.getRecipientId() : room.getSenderId();

            chatUserRepository.findById(partnerId).ifPresent(partner -> {
                if (partner.isShowSeenStatus()) {
                    kafkaTemplate.send(socketEventsTopic,
                            new SocketEvent(SocketEventType.MESSAGE, partnerId, "/queue/read-receipts",
                                    ReadReceiptNotification.builder()
                                            .conversationId(conversationId)
                                            .userId(userId)
                                            .lastReadMessageId(lastReadMessageId)
                                            .build()));
                }
            });
        });
    }
}
