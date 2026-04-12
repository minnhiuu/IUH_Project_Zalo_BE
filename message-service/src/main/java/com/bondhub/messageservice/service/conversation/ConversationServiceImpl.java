package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.response.*;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.client.FriendServiceClient;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final SecurityUtil securityUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;
    private final FriendServiceClient friendServiceClient;
    private final ConversationHelper helper;

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

        ChatUser partner = helper.resolvePartner(partnerId, currentUserId, userCache);
        boolean viewerCanSee = helper.canViewerSeeStatus(currentUserId, userCache);
        String baseUrl = helper.getBaseUrl();

        String friendshipStatus = null;
        try {
            ApiResponse<Map<String, String>> response = friendServiceClient.getFriendshipStatuses(Collections.singletonList(partnerId));
            if (response != null && response.data() != null) {
                friendshipStatus = response.data().get(partnerId);
            }
        } catch (Exception e) {
            log.error("Failed to fetch friendship status for user {}", partnerId, e);
        }

        return helper.buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee, friendshipStatus);
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

        Set<String> allUserIds = new HashSet<>();
        allUserIds.add(currentUserId);
        roomsPage.getContent().forEach(room -> {
            room.getMembers().forEach(m -> allUserIds.add(m.getUserId()));
            if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
                allUserIds.add(room.getLastMessage().getSenderId());
            }
        });

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

        boolean viewerCanSee = helper.canViewerSeeStatus(currentUserId, userCache);
        String baseUrl = helper.getBaseUrl();

        return PageResponse.fromPage(roomsPage, room -> {
            String partnerId = room.getMembers().stream()
                    .filter(helper::isActiveMember)
                    .map(ConversationMember::getUserId)
                    .filter(uid -> !uid.equals(currentUserId))
                    .findFirst()
                    .orElse(currentUserId);

            ChatUser cachedPartner = userCache.get(partnerId);
            boolean partnerMissingProfile = cachedPartner == null
                || cachedPartner.getFullName() == null
                || cachedPartner.getFullName().isBlank();

            if (partnerMissingProfile && !partnerId.equals(currentUserId)
                    && !partnerId.equals("ai-assistant-001")) {
                eventPublisher.publishEvent(new UserSyncEvent(partnerId));
            }

            ChatUser partner = helper.resolvePartner(partnerId, currentUserId, userCache);
            String friendshipStatus = friendshipStatusMap.get(partnerId);
            return helper.buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee, friendshipStatus);
        });
    }

    // ─────────────────────────── Đánh dấu đã đọc ───────────────────────────

    @Override
    public void markAsRead(String conversationId) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(currentUserId));
        if (!isMember) {
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }

        String finalReadId = room.getLastMessage() != null ? room.getLastMessage().getMessageId() : null;

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


    @Override
    public void deleteConversationForMe(String conversationId) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        conversation.getMembers().stream()
                .filter(m -> m.getUserId().equals(currentUserId))
                .findFirst()
                .ifPresent(member -> {
                    member.setActive(false);
                    member.setRemovedAt(LocalDateTime.now());
                });

        if (conversation.getDeletedBefore() == null) {
            conversation.setDeletedBefore(new HashMap<>());
        }
        conversation.getDeletedBefore().put(currentUserId, LocalDateTime.now());

        if (conversation.getUnreadCounts() != null) {
            conversation.getUnreadCounts().remove(currentUserId);
        }

        conversationRepository.save(conversation);
        log.info("[Conversation] User {} deleted conversation {}", currentUserId, conversationId);
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    private void broadcastReadReceipt(Conversation room, String currentUserId, String lastReadMessageId) {
        List<ConversationMember> otherMembers = room.getMembers().stream()
                .filter(helper::isActiveMember)
                .filter(m -> !m.getUserId().equals(currentUserId))
                .toList();

        if (otherMembers.isEmpty()) return;

        Set<String> memberIds = otherMembers.stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        otherMembers.forEach(m -> {
            ChatUser partnerUser = userCache.get(m.getUserId());
            if (partnerUser != null && partnerUser.isShowSeenStatus()) {
                helper.getKafkaTemplate().send(helper.getSocketEventsTopic(),
                        new SocketEvent(SocketEventType.MESSAGE, m.getUserId(),
                                "/queue/read-receipts",
                                ReadReceiptNotification.builder()
                                        .conversationId(room.getId())
                                        .userId(currentUserId)
                                        .lastReadMessageId(lastReadMessageId)
                                        .build()));
            }
        });
    }
}
