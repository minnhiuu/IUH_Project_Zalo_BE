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
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.client.FriendServiceClient;
import com.bondhub.messageservice.dto.response.ConversationMemberResponse;
import com.bondhub.messageservice.dto.response.ReadReceiptNotification;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.enums.MessageType;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
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

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final SecurityUtil securityUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoTemplate mongoTemplate;
    private final FriendServiceClient friendServiceClient;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${kafka.topics.socket-events}")
    private String socketEventsTopic;

    // ─────────────────────────── Core: Tạo / Lấy phòng chat ───────────────────────────

    @Override
    public Conversation getOrCreateDirectConversation(String userA, String userB) {
        return conversationRepository.findDirectConversation(userA, userB)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    String uniqueKey = (userA.compareTo(userB) < 0) ? userA + "_" + userB : userB + "_" + userA;
                    Conversation newRoom = Conversation.builder()
                            .uniqueKey(uniqueKey)
                            .members(new HashSet<>(Arrays.asList(
                                    ConversationMember.builder()
                                            .userId(userA).role(MemberRole.OWNER).joinedAt(now).build(),
                                    ConversationMember.builder()
                                            .userId(userB).role(MemberRole.MEMBER).joinedAt(now).build()
                            )))
                            .lastMessage(LastMessageInfo.builder().timestamp(now).build())
                            .build();
                    log.info("[Conversation] Creating new direct conversation between {} and {}", userA, userB);
                    return conversationRepository.save(newRoom);
                });
    }

    @Override
    public ConversationResponse getOrCreateConversationForUser(String partnerId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Conversation room = getOrCreateDirectConversation(currentUserId, partnerId);

        Set<String> userIds = new HashSet<>();
        userIds.add(currentUserId);
        userIds.add(partnerId);
        room.getMembers().forEach(m -> userIds.add(m.getUserId()));

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        ChatUser cachedPartner = userCache.get(partnerId);
        if (cachedPartner == null || cachedPartner.getFullName() == null || cachedPartner.getFullName().isBlank()) {
            if (!partnerId.equals(currentUserId) && !partnerId.equals("ai-assistant-001")) {
                eventPublisher.publishEvent(new UserSyncEvent(partnerId));
            }
        }

        ChatUser partner = resolvePartner(partnerId, currentUserId, userCache);
        boolean viewerCanSee = canViewerSeeStatus(currentUserId, userCache);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        String friendshipStatus = null;
        try {
            ApiResponse<Map<String, String>> response = friendServiceClient.getFriendshipStatuses(Collections.singletonList(partnerId));
            if (response != null && response.data() != null) {
                friendshipStatus = response.data().get(partnerId);
            }
        } catch (Exception e) {
            log.error("Failed to fetch friendship status for user {}", partnerId, e);
        }

        return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee, friendshipStatus);
    }

    // ─────────────────────────── Query: Danh sách phòng chat ───────────────────────────

    @Override
    public PageResponse<List<ConversationResponse>> getUserConversations(int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessage.timestamp"));
        Page<Conversation> roomsPage = conversationRepository.findAllByMembersUserId(currentUserId, pageable);
        log.info("[Conversation] Fetched page {} of conversations for user {} with {} rooms",
                page, currentUserId, roomsPage.getTotalElements());
        if (roomsPage.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        // Gom tất cả userId cần fetch trong 1 lượt
        Set<String> allUserIds = new HashSet<>();
        allUserIds.add(currentUserId);
        roomsPage.getContent().forEach(room ->
                room.getMembers().forEach(m -> allUserIds.add(m.getUserId()))
        );

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        Map<String, String> tempMap;
        try {
            ApiResponse<Map<String, String>> response = friendServiceClient.getFriendshipStatuses(new ArrayList<>(allUserIds));
            tempMap = (response != null && response.data() != null) ? response.data() : Collections.emptyMap();
        } catch (Exception e) {
            log.error("Failed to fetch batch friendship statuses", e);
            tempMap = Collections.emptyMap();
        }
        final Map<String, String> friendshipStatusMap = tempMap;

        boolean viewerCanSee = canViewerSeeStatus(currentUserId, userCache);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return PageResponse.fromPage(roomsPage, room -> {
            // Tìm partner = thành viên đầu tiên khác currentUser
            String partnerId = room.getMembers().stream()
                    .map(ConversationMember::getUserId)
                    .filter(uid -> !uid.equals(currentUserId))
                    .findFirst()
                    .orElse(currentUserId); // trường hợp chat với chính mình (My Documents)

            ChatUser cachedPartner = userCache.get(partnerId);
            boolean partnerMissingProfile = cachedPartner == null
                || cachedPartner.getFullName() == null
                || cachedPartner.getFullName().isBlank();

            // Trigger sync nếu partner chưa có trong cache
            if (partnerMissingProfile && !partnerId.equals(currentUserId)
                    && !partnerId.equals("ai-assistant-001")) {
                eventPublisher.publishEvent(new UserSyncEvent(partnerId));
            }

            ChatUser partner = resolvePartner(partnerId, currentUserId, userCache);
            String friendshipStatus = friendshipStatusMap.get(partnerId);
            return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee, friendshipStatus);
        });
    }

    // ─────────────────────────── Đánh dấu đã đọc ───────────────────────────

    @Override
    public void markAsRead(String conversationId) {
        String currentUserId = securityUtil.getCurrentUserId();

        // 1. Tìm phòng bằng ObjectId
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 2. Kiểm tra quyền: currentUser phải là thành viên
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(currentUserId));
        if (!isMember) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String finalReadId = room.getLastMessage() != null ? room.getLastMessage().getMessageId() : null;

        // 3. Cập nhật unreadCount và lastReadMessageId
        Query query = new Query(Criteria.where("id").is(conversationId)
                .and("members.userId").is(currentUserId));
        Update update = new Update().set("unreadCounts." + currentUserId, 0);
        if (finalReadId != null) {
            update.set("members.$.lastReadMessageId", finalReadId);
        }

        UpdateResult result = mongoTemplate.updateFirst(query, update, Conversation.class);

        if (result.getModifiedCount() > 0) {
            chatUserRepository.findById(currentUserId).ifPresent(user -> {
                if (user.isShowSeenStatus()) {
                    broadcastReadReceipt(room, currentUserId, finalReadId);
                }
            });
        }
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    /**
     * Resolve đối tượng ChatUser cho partner,
     * với logic đặc biệt cho "My Documents" (chat với chính mình).
     */
    private ChatUser resolvePartner(String partnerId, String currentUserId, Map<String, ChatUser> userCache) {
        if (partnerId.equals(currentUserId)) {
            return ChatUser.builder()
                    .id(partnerId)
                    .fullName("My Documents")
                    .avatar("cloud.png")
                    .showSeenStatus(false)
                    .build();
        }
        return userCache.getOrDefault(partnerId,
                ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());
    }

    private boolean canViewerSeeStatus(String currentUserId, Map<String, ChatUser> userCache) {
        ChatUser currentUser = userCache.get(currentUserId);
        return currentUser != null && currentUser.isShowSeenStatus();
    }

    private ConversationResponse buildConversationResponse(
            Conversation room, ChatUser partner, String currentUserId,
            Map<String, ChatUser> userCache, String baseUrl, boolean viewerCanSee, String friendshipStatus) {

        LastMessageInfo last = room.getLastMessage();
        List<ConversationMemberResponse> members = buildMembersWithCache(
                room, currentUserId, userCache, baseUrl, viewerCanSee);

        // Với Group: name/avatar lấy từ Conversation entity
        // Với 1-1: name/avatar lấy từ partner ChatUser
        String partnerDisplayName = safeDisplayName(partner != null ? partner.getFullName() : null);
        String displayName = room.isGroup() ? room.getName() : partnerDisplayName;
        if (displayName == null || displayName.isBlank()) {
            displayName = "Người dùng";
        }
        String displayAvatar = room.isGroup()
                ? (room.getAvatar() != null ? baseUrl + room.getAvatar() : null)
                : (partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null);

        boolean isFriend = "ACCEPTED".equals(friendshipStatus);
        String lastMsgDisplay = last != null ? last.getContent() : "";

        return ConversationResponse.builder()
                .id(room.getId())
                .recipientId(partner.getId())
                .name(displayName)
                .avatar(displayAvatar)
                .status(isFriend ? (room.isGroup() ? null : partner.getStatus()) : null)
                .lastSeenAt(isFriend ? (room.isGroup() ? null : partner.getLastUpdatedAt()) : null)
                .friendshipStatus(friendshipStatus)
                .isGroup(room.isGroup())
                .lastMessage(lastMsgDisplay)
                .lastMessageId(last != null ? last.getMessageId() : null)
                .lastMessageTime(last != null ? last.getTimestamp() : null)
                .isLastMessageFromMe(last != null && last.getSenderId() != null
                        && last.getSenderId().equals(currentUserId))
                .lastMessageType(last != null ? last.getType() : MessageType.CHAT)
                .unreadCount(room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(currentUserId, 0) : 0)
                .lastMessageStatus(last != null ? last.getStatus() : null)
                .members(members)
                .build();
    }

    private String safeDisplayName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Người dùng";
        }
        return fullName;
    }

    private List<ConversationMemberResponse> buildMembersWithCache(
            Conversation room, String currentUserId, Map<String, ChatUser> userCache,
            String baseUrl, boolean viewerCanSee) {

        return room.getMembers().stream()
                .filter(m -> !m.getUserId().equals(currentUserId))
                .map(m -> {
                    ChatUser memberInfo = userCache.get(m.getUserId());
                    boolean canSeeStatus = viewerCanSee && memberInfo != null
                            && memberInfo.isShowSeenStatus();

                    return ConversationMemberResponse.builder()
                            .userId(m.getUserId())
                            .fullName(memberInfo != null ? memberInfo.getFullName() : "Người dùng")
                            .avatar(memberInfo != null && memberInfo.getAvatar() != null
                                    ? baseUrl + memberInfo.getAvatar() : null)
                            .lastReadMessageId(canSeeStatus ? m.getLastReadMessageId() : null)
                            .role(m.getRole() != null ? m.getRole().name() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Broadcast read receipt tới tất cả thành viên khác trong phòng.
     */
    private void broadcastReadReceipt(Conversation room, String currentUserId, String lastReadMessageId) {
        room.getMembers().stream()
                .filter(m -> !m.getUserId().equals(currentUserId))
                .forEach(m -> {
                    chatUserRepository.findById(m.getUserId()).ifPresent(partnerUser -> {
                        if (partnerUser.isShowSeenStatus()) {
                            kafkaTemplate.send(socketEventsTopic,
                                    new SocketEvent(SocketEventType.MESSAGE, m.getUserId(),
                                            "/queue/read-receipts",
                                            ReadReceiptNotification.builder()
                                                    .conversationId(room.getId())
                                                    .userId(currentUserId)
                                                    .lastReadMessageId(lastReadMessageId)
                                                    .build()));
                        }
                    });
                });
    }
}
