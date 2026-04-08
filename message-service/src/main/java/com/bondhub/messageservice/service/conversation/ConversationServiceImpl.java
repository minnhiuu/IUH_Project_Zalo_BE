package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.enums.Status;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.enums.MessageType;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.response.*;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.common.dto.client.socketservice.SocketEvent;
import com.bondhub.common.enums.SocketEventType;
import com.bondhub.messageservice.client.FileServiceClient;
import com.bondhub.messageservice.mapper.MessageMapper;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.messageservice.service.message.MessageService;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bondhub.common.dto.PageResponse;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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
    private final MessageService messageService;
    private final FileServiceClient fileServiceClient;

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
                    Conversation newRoom = Conversation.builder()
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
        if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
            userIds.add(room.getLastMessage().getSenderId());
        }

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        ChatUser partner = resolvePartner(partnerId, currentUserId, userCache);
        boolean viewerCanSee = canViewerSeeStatus(currentUserId, userCache);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee);
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
        roomsPage.getContent().forEach(room -> {
            room.getMembers().forEach(m -> allUserIds.add(m.getUserId()));
            if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
                allUserIds.add(room.getLastMessage().getSenderId());
            }
        });

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        boolean viewerCanSee = canViewerSeeStatus(currentUserId, userCache);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return PageResponse.fromPage(roomsPage, room -> {
            // Tìm partner = thành viên đầu tiên khác currentUser
            String partnerId = room.getMembers().stream()
                    .filter(this::isActiveMember)
                    .map(ConversationMember::getUserId)
                    .filter(uid -> !uid.equals(currentUserId))
                    .findFirst()
                    .orElse(currentUserId); // trường hợp chat với chính mình (My Documents)

            // Trigger sync nếu partner chưa có trong cache
            if (!userCache.containsKey(partnerId) && !partnerId.equals(currentUserId)
                    && !partnerId.equals("ai-assistant-001")) {
                eventPublisher.publishEvent(new UserSyncEvent(partnerId));
            }

            ChatUser partner = resolvePartner(partnerId, currentUserId, userCache);
            return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee);
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
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
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

    @Override
    public ConversationResponse createGroupConversation(GroupConversationCreateRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();

        String groupName = (request.name() == null || request.name().isBlank()) ? null : request.name().trim();
        String avatarUrl = request.avatar();

        Set<String> memberIds = new LinkedHashSet<>(request.memberIds());
        memberIds.remove(currentUserId);

        if (memberIds.size() < 2) {
            throw new AppException(ErrorCode.CHAT_INVALID_MEMBER_COUNT);
        }

        List<ChatUser> users = chatUserRepository.findAllById(memberIds);
        if (users.size() != memberIds.size()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();

        Set<ConversationMember> members = new HashSet<>();

        members.add(ConversationMember.builder()
                .userId(currentUserId)
                .role(MemberRole.OWNER)
                .joinedAt(now)
                .build());

        memberIds.forEach(id -> members.add(
                ConversationMember.builder()
                        .userId(id)
                        .role(MemberRole.MEMBER)
                        .joinedAt(now)
                        .build()
        ));

        Map<String, Integer> unreadCounts = new HashMap<>();
        members.forEach(m -> unreadCounts.put(m.getUserId(), 0));

        Conversation conversation = Conversation.builder()
                .name(groupName)
                .avatar(avatarUrl)
                .isGroup(true)
                .members(members)
                .unreadCounts(unreadCounts)
                .lastMessage(LastMessageInfo.builder().timestamp(now).build())
                .build();

        Conversation saved = conversationRepository.save(conversation);

        ChatUser actor = chatUserRepository.findById(currentUserId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Người dùng";
        String actorAvatar = actor != null ? actor.getAvatar() : null;
        Map<String, String> userNameMap = users.stream()
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getFullName));
        List<String> createTargetIds = new ArrayList<>(memberIds);
        List<String> createTargetNames = createTargetIds.stream()
                .map(id -> userNameMap.getOrDefault(id, "Người dùng"))
                .toList();

        messageService.sendSystemMessage(saved.getId(), currentUserId, actorName,
                SystemActionType.CREATE_GROUP,
                Map.of(
                        "targetIds", createTargetIds,
                        "payload", Map.of("targetNames", createTargetNames)
                ));

        log.info("[Conversation] Created group conversation {} by user {} with {} members",
                saved.getId(), saved.getMembers().size(), currentUserId);

        broadcastConversationUpdate(saved.getId());
        return buildConversationResponseForCurrentUser(saved, currentUserId);
    }

    @Override
    public ConversationResponse addMembersToGroup(String conversationId, List<String> memberIds) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!conversation.isGroup()) {
            throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        }
        assertMember(conversation, currentUserId);

        Set<String> requestedIds = new LinkedHashSet<>(memberIds != null ? memberIds : Collections.emptyList());
        requestedIds.remove(currentUserId);

        if (requestedIds.isEmpty()) {
            return buildConversationResponseForCurrentUser(conversation, currentUserId);
        }

        Map<String, ConversationMember> existingMembersById = conversation.getMembers().stream()
                .collect(Collectors.toMap(ConversationMember::getUserId, m -> m, (a, b) -> a));

        Set<String> existingActiveMemberIds = conversation.getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        requestedIds.removeAll(existingActiveMemberIds);

        if (requestedIds.isEmpty()) {
            return buildConversationResponseForCurrentUser(conversation, currentUserId);
        }

        List<ChatUser> users = chatUserRepository.findAllById(requestedIds);
        if (users.size() != requestedIds.size()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        requestedIds.forEach(id -> {
            ConversationMember existingMember = existingMembersById.get(id);
            if (existingMember != null) {
                existingMember.setActive(true);
                existingMember.setRemovedAt(null);
                existingMember.setRemovedBy(null);
                existingMember.setRole(MemberRole.MEMBER);
                return;
            }
            conversation.getMembers().add(
                    ConversationMember.builder()
                            .userId(id)
                            .role(MemberRole.MEMBER)
                            .joinedAt(now)
                            .build()
            );
        });

        if (conversation.getUnreadCounts() == null) {
            conversation.setUnreadCounts(new HashMap<>());
        }
        requestedIds.forEach(id -> conversation.getUnreadCounts().putIfAbsent(id, 0));

        Conversation saved = conversationRepository.save(conversation);

        ChatUser actor = chatUserRepository.findById(currentUserId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Người dùng";
        Map<String, String> requestedNameMap = users.stream()
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getFullName));
        List<String> addedTargetIds = new ArrayList<>(requestedIds);
        List<String> addedTargetNames = addedTargetIds.stream()
                .map(id -> requestedNameMap.getOrDefault(id, "Người dùng"))
                .toList();

        messageService.sendSystemMessage(conversationId, currentUserId, actorName,
                SystemActionType.ADD_MEMBERS,
                Map.of(
                        "targetIds", addedTargetIds,
                        "payload", Map.of("targetNames", addedTargetNames)
                ));

        broadcastConversationUpdate(saved.getId());
        return buildConversationResponseForCurrentUser(saved, currentUserId);
    }

    @Override
    public ConversationResponse removeMemberFromGroup(String conversationId, String targetUserId) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!conversation.isGroup()) {
            throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        }
        assertMember(conversation, currentUserId);

        if (targetUserId == null || targetUserId.isBlank() || targetUserId.equals(currentUserId)) {
            throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_YOURSELF);
        }

        ConversationMember actor = getMemberOrThrow(conversation, currentUserId);
        ConversationMember target = getMemberOrThrow(conversation, targetUserId);

        assertOwnerOrAdmin(actor);
        assertCanRemoveMember(actor, target);

        target.setActive(false);
        target.setRemovedAt(LocalDateTime.now());
        target.setRemovedBy(currentUserId);

        if (conversation.getUnreadCounts() != null) {
            conversation.getUnreadCounts().remove(targetUserId);
        }

        Conversation saved = conversationRepository.save(conversation);

        ChatUser actorUser = chatUserRepository.findById(currentUserId).orElse(null);
        String actorName = actorUser != null ? actorUser.getFullName() : "Người dùng";
        ChatUser targetUser = chatUserRepository.findById(targetUserId).orElse(null);
        String targetName = targetUser != null ? targetUser.getFullName() : "Người dùng";

        messageService.sendSystemMessage(conversationId, currentUserId, actorName,
                SystemActionType.REMOVE_MEMBER,
                Map.of(
                        "targetIds", List.of(targetUserId),
                        "payload", Map.of("targetName", targetName)
                ));

        broadcastConversationUpdate(saved.getId());
        return buildConversationResponseForCurrentUser(saved, currentUserId);
    }

    @Override
    public ConversationResponse updateGroupName(String conversationId, String name) {
        String currentUserId = securityUtil.getCurrentUserId();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!conversation.isGroup()) {
            throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        }
        assertMember(conversation, currentUserId);

        String oldName = conversation.getName();
        if (oldName != null && oldName.equals(name)) {
            return buildConversationResponseForCurrentUser(conversation, currentUserId);
        }
        conversation.setName(name);

        ChatUser actor = chatUserRepository.findById(currentUserId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "User";

        conversationRepository.save(conversation);

        messageService.sendSystemMessage(conversationId, currentUserId, actorName, SystemActionType.UPDATE_NAME,
                Map.of("payload", Map.of(
                        "oldName", oldName,
                        "newName", name
                )));

        broadcastConversationUpdate(conversationId);
        return buildConversationResponseForCurrentUser(conversation, currentUserId);
    }

    @Override
    public ConversationResponse updateGroupAvatar(String conversationId, MultipartFile file) {
        String currentUserId = securityUtil.getCurrentUserId();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!conversation.isGroup()) {
            throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        }
        assertMember(conversation, currentUserId);

        if (file != null && !file.isEmpty()) {
            ApiResponse<FileUploadResponse> uploadResponse = fileServiceClient.upload(file);
            if (uploadResponse != null && uploadResponse.data() != null) {
                String oldAvatarKey = conversation.getAvatar();
                String avatarKey = uploadResponse.data().key();

                conversation.setAvatar(avatarKey);

                ChatUser actor = chatUserRepository.findById(currentUserId).orElse(null);
                String actorName = actor != null ? actor.getFullName() : "User";

                conversationRepository.save(conversation);

                if (oldAvatarKey != null && !oldAvatarKey.isBlank() && !oldAvatarKey.equals(avatarKey)) {
                    try {
                        fileServiceClient.delete(oldAvatarKey);
                        log.info("[Conversation] Deleted old avatar {} for conversation {}", oldAvatarKey, conversationId);
                    } catch (Exception e) {
                        log.warn("[Conversation] Failed to delete old avatar {} for conversation {}: {}",
                                oldAvatarKey, conversationId, e.getMessage());
                    }
                }

                messageService.sendSystemMessage(conversationId, currentUserId, actorName, SystemActionType.UPDATE_AVATAR,
                        Map.of());

                broadcastConversationUpdate(conversationId);
                return buildConversationResponseForCurrentUser(conversation, currentUserId);
            }
        }

        broadcastConversationUpdate(conversation);
        return buildConversationResponseForCurrentUser(conversation, currentUserId);
    }


    @Override
    public void broadcastConversationUpdate(String conversationId) {
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        broadcastConversationUpdate(room);
    }

    @Override
    public void broadcastConversationUpdate(Conversation room) {
        Set<String> userIds = room.getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
            userIds.add(room.getLastMessage().getSenderId());
        }

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        for (ConversationMember member : room.getMembers()) {
            if (!isActiveMember(member)) {
                continue;
            }
            String viewerId = member.getUserId();
            boolean viewerCanSee = canViewerSeeStatus(viewerId, userCache);

            // Xử lý partner cho 1-1
            ChatUser partner = null;
            if (!room.isGroup()) {
                String partnerId = room.getMembers().stream()
                        .filter(this::isActiveMember)
                        .map(ConversationMember::getUserId)
                        .filter(uid -> !uid.equals(viewerId))
                        .findFirst()
                        .orElse(viewerId);
                partner = resolvePartner(partnerId, viewerId, userCache);
            }

            ConversationResponse payload = buildConversationResponse(
                    room,
                    partner,
                    viewerId,
                    userCache,
                    baseUrl,
                    viewerCanSee
            );

            kafkaTemplate.send(socketEventsTopic,
                    new SocketEvent(
                            SocketEventType.CONVERSATION,
                            viewerId,
                            "/queue/conversations",
                            payload
                    ));
        }
    }

    private ConversationResponse buildConversationResponseForCurrentUser(
            Conversation room,
            String currentUserId
    ) {
        Set<String> allUserIds = room.getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());
        if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
            allUserIds.add(room.getLastMessage().getSenderId());
        }

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        // Xử lý partner cho 1-1
        ChatUser partner = null;
        if (!room.isGroup()) {
            String partnerId = room.getMembers().stream()
                    .filter(this::isActiveMember)
                    .map(ConversationMember::getUserId)
                    .filter(uid -> !uid.equals(currentUserId))
                    .findFirst()
                    .orElse(currentUserId);
            partner = resolvePartner(partnerId, currentUserId, userCache);
        }

        boolean viewerCanSee = canViewerSeeStatus(currentUserId, userCache);

        return buildConversationResponse(
                room,
                partner,
                currentUserId,
                userCache,
                baseUrl,
                viewerCanSee
        );
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    private void assertMember(Conversation room, String userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId) && isActiveMember(m));
        if (!isMember) {
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }
    }

    private ConversationMember getMemberOrThrow(Conversation room, String userId) {
        return room.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && isActiveMember(m))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND));
    }

    private boolean isActiveMember(ConversationMember member) {
        return !Boolean.FALSE.equals(member.getActive());
    }

    private MemberRole resolveRole(ConversationMember member) {
        return member.getRole() != null ? member.getRole() : MemberRole.MEMBER;
    }

    private void assertOwnerOrAdmin(ConversationMember actor) {
        MemberRole actorRole = resolveRole(actor);
        if (actorRole == MemberRole.MEMBER) {
            throw new AppException(ErrorCode.CHAT_NOT_GROUP_MANAGER);
        }
    }

    private void assertCanRemoveMember(ConversationMember actor, ConversationMember target) {
        MemberRole actorRole = resolveRole(actor);
        MemberRole targetRole = resolveRole(target);

        if (targetRole == MemberRole.OWNER) {
            throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_OWNER);
        }

        if (actorRole == MemberRole.ADMIN && targetRole != MemberRole.MEMBER) {
            throw new AppException(ErrorCode.CHAT_ADMIN_CAN_ONLY_REMOVE_MEMBER);
        }
    }

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
            Map<String, ChatUser> userCache, String baseUrl, boolean viewerCanSee) {

        LastMessageInfo last = room.getLastMessage();
        List<ConversationMemberResponse> members = buildMembersWithCache(
                room, currentUserId, userCache, baseUrl, viewerCanSee);

        // Với Group: name/avatar lấy từ Conversation entity
        // Với 1-1: name/avatar lấy từ partner ChatUser
        String displayName = room.isGroup() ? room.getName() : partner.getFullName();
        String displayAvatar = room.isGroup()
                ? (room.getAvatar() != null ? baseUrl + room.getAvatar() : null)
                : (partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null);

        Status displayStatus = room.isGroup()
                ? (room.getMembers().stream()
                .filter(m -> {
                    if (!isActiveMember(m)) return false;
                    if (m.getUserId().equals(currentUserId)) return false;
                    ChatUser memberInfo = userCache.get(m.getUserId());
                    return memberInfo == null || !currentUserId.equals(memberInfo.getAccountId());
                })
                .map(m -> userCache.get(m.getUserId()))
                .filter(Objects::nonNull)
                .anyMatch(u -> u.getStatus() == Status.ONLINE) ? Status.ONLINE : Status.OFFLINE)
                : partner.getStatus();

        return ConversationResponse.builder()
                .id(room.getId())
                .name(displayName)
                .avatar(displayAvatar)
                .status(displayStatus)
                .lastSeenAt(room.isGroup() ? null : toOffset(partner.getLastUpdatedAt()))
                .isGroup(room.isGroup())
                .isDisbanded(room.isDisbanded())
                .unreadCount(room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(currentUserId, 0) : 0)
                .lastMessage(last != null ? LastMessageResponse.builder()
                        .id(last.getMessageId())
                        .senderId(last.getSenderId())
                        .senderName(last.getSenderId() != null
                                ? userCache.getOrDefault(last.getSenderId(),
                                ChatUser.builder().fullName("").build()).getFullName() : null)
                        .content(last.getContent())
                        .timestamp(toOffset(last.getTimestamp()))
                        .type(last.getType())
                        .status(last.getStatus())
                        .isFromMe(last.getSenderId() != null && last.getSenderId().equals(currentUserId))
                        .metadata(last.getMetadata())
                        .build() : null)
                .members(members)
                .build();
    }

    private List<ConversationMemberResponse> buildMembersWithCache(
            Conversation room, String currentUserId, Map<String, ChatUser> userCache,
            String baseUrl, boolean viewerCanSee) {

        return room.getMembers().stream()
                .filter(this::isActiveMember)
                .filter(m -> room.isGroup() || !m.getUserId().equals(currentUserId))
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
                            .role(m.getRole() != null ? m.getRole() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Broadcast read receipt tới tất cả thành viên khác trong phòng.
     */
    private void broadcastReadReceipt(Conversation room, String currentUserId, String lastReadMessageId) {
        room.getMembers().stream()
                .filter(this::isActiveMember)
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

    @Override
    public void disbandGroup(String conversationId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!conversation.isGroup()) {
            throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        }

        boolean isOwner = conversation.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(currentUserId)
                        && isActiveMember(m)
                        && m.getRole() == MemberRole.OWNER);

        if (!isOwner) {
            log.warn("[Conversation] User {} tried to disband group {} but is not the owner", currentUserId, conversationId);
            throw new AppException(ErrorCode.CHAT_NOT_OWNER);
        }

        conversation.setDisbanded(true);
        conversationRepository.save(conversation);

        messageService.deleteAllMessagesByConversationId(conversationId);

        ChatUser actor = chatUserRepository.findById(currentUserId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Owner";
        messageService.sendSystemMessage(conversationId, currentUserId, actorName, SystemActionType.DISBAND_GROUP, Map.of());

        broadcastConversationUpdate(conversationId);

        log.info("[Conversation] Group {} has been disbanded by owner {}", conversationId, currentUserId);
    }

    @Override
    public void leaveGroup(String conversationId, boolean silent) {
        String currentUserId = securityUtil.getCurrentUserId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!conversation.isGroup()) {
            throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        }
        assertMember(conversation, currentUserId);

        ConversationMember currentMember = getMemberOrThrow(conversation, currentUserId);
        if (resolveRole(currentMember) == MemberRole.OWNER) {
            throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_OWNER);
        }

        ChatUser actor = chatUserRepository.findById(currentUserId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Người dùng";
        String actorAvatar = actor != null ? actor.getAvatar() : null;

        if (!silent) {
            messageService.sendSystemMessage(conversationId, currentUserId, actorName,
                    SystemActionType.LEAVE_GROUP, Map.of());
        }

        Query query = new Query(Criteria.where("id").is(conversationId));
        Update update = new Update().pull("members", Query.query(Criteria.where("userId").is(currentUserId)));
        update.unset("unreadCounts." + currentUserId);
        mongoTemplate.updateFirst(query, update, Conversation.class);

        Conversation updatedConversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (silent) {
            sendLeaveSystemMessageToOwnerAdmin(updatedConversation, currentUserId, actorName, actorAvatar);
        }

        broadcastConversationUpdate(updatedConversation);

        log.info("[Conversation] User {} left group {} (silent={})", currentUserId, conversationId, silent);
    }

    private void sendLeaveSystemMessageToOwnerAdmin(Conversation conversation,
                                                    String actorId,
                                                    String actorName,
                                                    String actorAvatar) {
        if (conversation == null || conversation.getMembers() == null) {
            return;
        }

        Map<String, Object> metadata = Map.of("action", SystemActionType.LEAVE_GROUP.name(), "silent", true);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        String avatarUrl = actorAvatar != null ? baseUrl + actorAvatar : null;

        conversation.getMembers().stream()
                .filter(this::isActiveMember)
                .filter(member -> {
                    MemberRole role = resolveRole(member);
                    return role == MemberRole.OWNER || role == MemberRole.ADMIN;
                })
                .forEach(member -> {
                    ChatNotification notification = ChatNotification.builder()
                            .id("silent-leave-" + UUID.randomUUID())
                            .conversationId(conversation.getId())
                            .senderId(actorId)
                            .senderName(actorName)
                            .senderAvatar(avatarUrl)
                            .content(null)
                            .type(MessageType.SYSTEM)
                            .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                            .metadata(metadata)
                            .build();

                    kafkaTemplate.send(socketEventsTopic,
                            new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                                    "/queue/messages", notification));
                });
    }

    @Override
    public void deleteConversationForMe(String conversationId) {
        String currentUserId = securityUtil.getCurrentUserId();

        messageService.deleteAllMessagesByConversationIdForMe(conversationId);

        Query query = new Query(Criteria.where("id").is(conversationId));
        Update update = new Update().pull("members", Query.query(Criteria.where("userId").is(currentUserId)));
        update.unset("unreadCounts." + currentUserId);

        mongoTemplate.updateFirst(query, update, Conversation.class);
        log.info("[Conversation] User {} deleted conversation {}", currentUserId, conversationId);
    }

    @Override
    public Map<String, List<SearchMemberResponse>> getFriendsDirectory(String conversationId) {
        String currentUserId = securityUtil.getCurrentUserId();
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        final Set<String> memberIds = (conversationId == null || conversationId.isBlank())
                ? new HashSet<>()
                : conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
        if (currentUser == null || currentUser.getFriendIds().isEmpty()) {
            return new TreeMap<>();
        }

        List<ChatUser> friends = chatUserRepository.findAllById(currentUser.getFriendIds());

        return friends.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(u -> SearchMemberResponse.builder()
                        .userId(u.getId())
                        .fullName(u.getFullName())
                        .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                        .isAlreadyMember(memberIds.contains(u.getId()))
                        .build())
                .sorted(Comparator.comparing(u -> normalizeForSort(u.fullName())))
                .collect(Collectors.groupingBy(
                        u -> getNormalizedFirstLetter(u.fullName()),
                        TreeMap::new,
                        Collectors.toList()
                ));
    }


    private String normalizeForSort(String name) {
        if (name == null || name.isBlank()) return "";
        String normalized = Normalizer.normalize(name.trim().toUpperCase().replace('Đ', 'D'), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private String getNormalizedFirstLetter(String name) {
        String normalized = normalizeForSort(name);
        if (normalized.isEmpty()) return "#";

        char firstChar = normalized.charAt(0);
        if (firstChar >= 'A' && firstChar <= 'Z') return String.valueOf(firstChar);
        if (Character.isDigit(firstChar)) return String.valueOf(firstChar);
        return "#";
    }

    @Override
    public PageResponse<List<SearchMemberResponse>> searchMembersToAdd(String conversationId, String query, int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);

        final Set<String> memberIds = (conversationId == null || conversationId.isBlank())
                ? new HashSet<>()
                : conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        Page<ChatUser> candidatesPage;

        if (isPhoneNumber(query)) {
            Optional<ChatUser> userOpt = chatUserRepository.findByPhoneNumber(query.trim())
                    .filter(u -> !u.getId().equals(currentUserId));
            List<ChatUser> list = userOpt.map(Collections::singletonList).orElse(Collections.emptyList());
            candidatesPage = new PageImpl<>(list, pageable, list.size());
        } else {
            ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
            if (currentUser != null && !currentUser.getFriendIds().isEmpty()) {
                Set<String> friendIds = new HashSet<>(currentUser.getFriendIds());
                friendIds.remove(currentUserId);
                candidatesPage = chatUserRepository.findByIdInAndFullNameContainingIgnoreCase(
                        friendIds, query.trim(), pageable);
            } else {
                candidatesPage = Page.empty(pageable);
            }
        }

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        return PageResponse.fromPage(candidatesPage, u -> SearchMemberResponse.builder()
                .userId(u.getId())
                .fullName(u.getFullName())
                .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                .phoneNumber(isPhoneNumber(query) ? u.getPhoneNumber() : null)
                .isAlreadyMember(memberIds.contains(u.getId()))
                .build());
    }

    @Override
    public PageResponse<List<GroupMemberListItemResponse>> getGroupMembers(String conversationId, String query, int page, int size) {
        String currentUserId = securityUtil.getCurrentUserId();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!conversation.isGroup()) {
            throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        }
        assertMember(conversation, currentUserId);

        Set<String> friendIds = chatUserRepository.findById(currentUserId)
                .map(ChatUser::getFriendIds)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        friendIds.remove(currentUserId);

        String normalizedQuery = query == null ? "" : query.trim();
        boolean hasQuery = !normalizedQuery.isBlank();
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        List<String> friendIdList = new ArrayList<>(friendIds);

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));

        List<AggregationOperation> commonStages = new ArrayList<>();
        commonStages.add(Aggregation.match(Criteria.where("_id").is(conversationId).and("isGroup").is(true)));
        commonStages.add(Aggregation.unwind("members"));
        commonStages.add(Aggregation.match(Criteria.where("members.active").ne(false)));
        commonStages.add(context -> new Document("$lookup", new Document()
                .append("from", "chat_users")
                .append("let", new Document("memberUserId", "$members.userId"))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$or", List.of(
                                new Document("$eq", List.of("$_id", "$$memberUserId")),
                                new Document("$eq", List.of(
                                        "$_id",
                                        new Document("$convert", new Document("input", "$$memberUserId")
                                                .append("to", "objectId")
                                                .append("onError", null)
                                                .append("onNull", null))
                                ))
                        ))))
                ))
                .append("as", "user")));
        commonStages.add(Aggregation.unwind("user", true));

        if (hasQuery) {
            String escaped = Pattern.quote(normalizedQuery);
            commonStages.add(Aggregation.match(new Criteria().orOperator(
                    Criteria.where("user.fullName").regex(escaped, "i"),
                    Criteria.where("user.phoneNumber").regex(escaped, "i")
            )));
        }

        Document sortBucketExpr = new Document("$switch", new Document("branches", List.of(
                new Document("case", new Document("$eq", List.of("$members.role", "OWNER"))).append("then", 0),
                new Document("case", new Document("$eq", List.of("$members.role", "ADMIN"))).append("then", 1)
        )).append("default",
                new Document("$cond", List.of(
                        new Document("$in", List.of("$members.userId", friendIdList)),
                        3,
                        2
                ))));

        Document addSortFieldsDoc = new Document("sortBucket", sortBucketExpr)
                .append("nameSort", new Document("$toLower", new Document("$ifNull", List.of("$user.fullName", ""))));

        Document projectFieldsDoc = new Document()
                .append("_id", 0)
                .append("userId", "$members.userId")
                .append("fullName", new Document("$ifNull", List.of("$user.fullName", "Người dùng")))
                .append("avatar", "$user.avatar")
                .append("phoneNumber", "$user.phoneNumber")
                .append("role", "$members.role")
                .append("joinedAt", "$members.joinedAt");

        Document facetDoc = new Document("$facet", new Document()
                .append("metadata", List.of(new Document("$count", "totalItems")))
                .append("data", List.of(
                        new Document("$addFields", addSortFieldsDoc),
                        new Document("$sort", new Document("sortBucket", 1).append("nameSort", 1).append("members.joinedAt", 1)),
                        new Document("$skip", pageable.getOffset()),
                        new Document("$limit", pageable.getPageSize()),
                        new Document("$project", projectFieldsDoc)
                )));

        List<AggregationOperation> pipeline = new ArrayList<>(commonStages);
        pipeline.add(context -> facetDoc);

        AggregationResults<Document> aggregated = mongoTemplate.aggregate(
                Aggregation.newAggregation(pipeline),
                "conversations",
                Document.class
        );

        Document root = aggregated.getUniqueMappedResult();
        List<Document> metadata = extractDocumentList(root != null ? root.get("metadata") : null);
        List<Document> dataDocs = extractDocumentList(root != null ? root.get("data") : null);
        int totalItems = !metadata.isEmpty()
                ? metadata.get(0).getInteger("totalItems", 0)
                : 0;

        List<GroupMemberListItemResponse> pageData = dataDocs.stream()
                .map(doc -> {
                    String userId = doc.getString("userId");
                    String fullName = doc.getString("fullName") != null ? doc.getString("fullName") : "Người dùng";
                    String avatar = doc.getString("avatar");
                    String phoneNumber = doc.getString("phoneNumber");
                    String roleRaw = doc.getString("role");

                    MemberRole role = roleRaw != null ? MemberRole.valueOf(roleRaw) : MemberRole.MEMBER;
                    boolean isCurrentUser = currentUserId.equals(userId);
                    boolean isFriend = friendIds.contains(userId);

                    return GroupMemberListItemResponse.builder()
                            .userId(userId)
                            .fullName(fullName)
                            .avatar(avatar != null ? baseUrl + avatar : null)
                            .phoneNumber(phoneNumber)
                            .role(role)
                            .joinedAt(toOffsetFromMongo(doc.get("joinedAt")))
                            .isCurrentUser(isCurrentUser)
                            .isFriend(isFriend)
                            .build();
                })
                .toList();

        return PageResponse.<List<GroupMemberListItemResponse>>builder()
                .data(pageData)
                .page(pageable.getPageNumber())
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageable.getPageSize()))
                .limit(pageable.getPageSize())
                .totalItems(totalItems)
                .build();
    }

    private List<Document> extractDocumentList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Document> documents = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Document document) {
                documents.add(document);
            }
        }
        return documents;
    }

    private OffsetDateTime toOffsetFromMongo(Object time) {
        if (time == null) return null;
        if (time instanceof LocalDateTime localDateTime) {
            return toOffset(localDateTime);
        }
        if (time instanceof Date date) {
            Instant instant = date.toInstant();
            return instant.atOffset(ZoneOffset.ofHours(7));
        }
        return null;
    }

    private boolean isPhoneNumber(String query) {
        return query.matches("\\d{9,11}");
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        if (time == null) return null;
        return time.atOffset(ZoneOffset.ofHours(7));
    }
}
