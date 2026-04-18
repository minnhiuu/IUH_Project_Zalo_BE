package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.event.group.GroupMemberChangedEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.common.utils.PhoneUtil;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.request.LeaveGroupRequest;
import com.bondhub.messageservice.dto.request.UpdateGroupSettingsRequest;
import com.bondhub.messageservice.dto.response.AdminMemberResponse;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.GroupMemberListItemResponse;
import com.bondhub.messageservice.dto.response.SearchMemberResponse;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.GroupSettings;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.enums.JoinMethod;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.service.message.SystemMessageService;
import com.bondhub.messageservice.client.FileServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupConversationServiceImpl implements GroupConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final SystemMessageService systemMessageService;
    private final FileServiceClient fileServiceClient;
    private final ConversationHelper helper;
    private final GroupInviteAsyncService groupInviteAsyncService;

    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    public ConversationResponse createGroupConversation(GroupConversationCreateRequest request) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();

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

        // Check for duplicate group: same name and same active members → return existing group
        if (groupName != null) {
            Set<String> allMemberIds = new LinkedHashSet<>(memberIds);
            allMemberIds.add(currentUserId);
            Optional<Conversation> existingGroup = findDuplicateGroup(groupName, allMemberIds);
            if (existingGroup.isPresent()) {
                log.info("[Group] Duplicate group '{}' detected, returning existing group {}", groupName, existingGroup.get().getId());
                return helper.buildConversationResponseForCurrentUser(existingGroup.get(), currentUserId);
            }
        }

        // Separate friends and non-friends
        ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
        Set<String> friendIds = (currentUser != null && currentUser.getFriendIds() != null)
                ? currentUser.getFriendIds() : Collections.emptySet();
        Set<String> friendMemberIds = memberIds.stream().filter(friendIds::contains).collect(Collectors.toCollection(LinkedHashSet::new));

        if (friendMemberIds.isEmpty()) {
            throw new AppException(ErrorCode.CHAT_NEED_AT_LEAST_ONE_FRIEND);
        }

        Set<String> nonFriendMemberIds = memberIds.stream().filter(id -> !friendIds.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));

        LocalDateTime now = LocalDateTime.now();

        // Only add friends as direct members; non-friends will receive invite links
        Set<ConversationMember> members = new HashSet<>();
        members.add(ConversationMember.builder()
                .userId(currentUserId).role(MemberRole.OWNER).joinedAt(now)
                .joinMethod(JoinMethod.ADDED_BY_MEMBER).addedBy(currentUserId).build());
        friendMemberIds.forEach(id -> members.add(
                ConversationMember.builder().userId(id).role(MemberRole.MEMBER).joinedAt(now)
                .joinMethod(JoinMethod.ADDED_BY_MEMBER).addedBy(currentUserId).build()));

        Map<String, Integer> unreadCounts = new HashMap<>();
        members.forEach(m -> unreadCounts.put(m.getUserId(), 0));

        // If there are non-friend members, enable join link
        boolean hasNonFriends = !nonFriendMemberIds.isEmpty();
        GroupSettings settings = GroupSettings.builder()
                .joinByLinkEnabled(hasNonFriends)
                .build();
        String joinLinkToken = hasNonFriends ? UUID.randomUUID().toString().replace("-", "") : null;

        Conversation conversation = Conversation.builder()
                .name(groupName).avatar(avatarUrl).isGroup(true)
                .members(members).unreadCounts(unreadCounts)
                .settings(settings)
                .joinLinkToken(joinLinkToken)
                .invitedUserIds(hasNonFriends ? new HashSet<>(nonFriendMemberIds) : new HashSet<>())
                .lastMessage(LastMessageInfo.builder().timestamp(now).build())
                .build();

        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        Map<String, String> userNameMap = users.stream()
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getFullName));
        Map<String, String> userAvatarMap = users.stream()
                .filter(u -> u.getAvatar() != null)
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getAvatar));
        List<String> createTargetIds = new ArrayList<>(friendMemberIds);
        List<String> createTargetNames = createTargetIds.stream()
                .map(id -> userNameMap.getOrDefault(id, "Người dùng")).toList();
        List<String> createTargetAvatars = createTargetIds.stream()
                .map(id -> userAvatarMap.getOrDefault(id, "")).toList();

        systemMessageService.sendSystemMessage(saved.getId(), currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.CREATE_GROUP,
                Map.of("targetIds", createTargetIds,
                        "payload", Map.of("targetNames", createTargetNames, "targetAvatars", createTargetAvatars)));

        log.info("[Group] Created group {} by user {} with {} direct members and {} pending invite(s)",
                saved.getId(), currentUserId, saved.getMembers().size(), nonFriendMemberIds.size());
        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse addMembersToGroup(String conversationId, List<String> memberIds) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);

        Set<String> requestedIds = new LinkedHashSet<>(memberIds != null ? memberIds : Collections.emptyList());
        requestedIds.remove(currentUserId);
        if (requestedIds.isEmpty()) return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);

        // Check group block list
        Set<String> blockedIds = getBlockedUserIds(conversation);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        MemberRole actorRole = helper.resolveRole(actor);
        Set<String> blockedInRequest = requestedIds.stream().filter(blockedIds::contains).collect(Collectors.toSet());
        if (!blockedInRequest.isEmpty()) {
            if (actorRole == MemberRole.OWNER || actorRole == MemberRole.ADMIN) {
                // Owner re-adding blocked users → auto-unblock them
                conversation.getBlockedUserIds().removeAll(blockedInRequest);
            } else {
                // Member tries to add blocked users → send system message and skip blocked users
                var actorInfo = helper.fetchActorInfo(currentUserId);
                for (String blockedUserId : blockedInRequest) {
                    var targetInfo = helper.fetchActorInfo(blockedUserId);
                    systemMessageService.sendSystemMessage(conversationId, currentUserId,
                            actorInfo.name(), actorInfo.avatar(),
                            SystemActionType.BLOCKED_FROM_JOINING,
                            Map.of("targetIds", List.of(blockedUserId),
                                    "payload", Map.of("targetName", targetInfo.name(),
                                            "targetAvatar", targetInfo.avatar() != null ? targetInfo.avatar() : "")),
                            Set.of(currentUserId));
                }
                requestedIds.removeAll(blockedInRequest);
                if (requestedIds.isEmpty()) return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);
            }
        }

        // Check self-blocked users (left group with blockReJoin)
        Set<String> selfBlockedIds = conversation.getSelfBlockedUserIds() != null ? conversation.getSelfBlockedUserIds() : Collections.emptySet();
        Set<String> selfBlockedInRequest = requestedIds.stream().filter(selfBlockedIds::contains).collect(Collectors.toSet());
        if (!selfBlockedInRequest.isEmpty()) {
            var actorInfoForSelfBlocked = helper.fetchActorInfo(currentUserId);
            GroupSettings settings = conversation.getSettings();
            boolean joinLinkEnabled = settings != null && settings.isJoinByLinkEnabled()
                    && conversation.getJoinLinkToken() != null;

            for (String selfBlockedUserId : selfBlockedInRequest) {
                var targetInfo = helper.fetchActorInfo(selfBlockedUserId);

                // Send system message visible only to actor
                systemMessageService.sendSystemMessage(conversationId, currentUserId,
                        actorInfoForSelfBlocked.name(), actorInfoForSelfBlocked.avatar(),
                        SystemActionType.SELF_BLOCKED_FROM_JOINING,
                        Map.of("targetIds", List.of(selfBlockedUserId),
                                "payload", Map.of(
                                        "targetName", targetInfo.name(),
                                        "targetAvatar", targetInfo.avatar() != null ? targetInfo.avatar() : "",
                                        "joinLinkEnabled", joinLinkEnabled)),
                        Set.of(currentUserId));

                // If join link is enabled, send join link as a regular message in the direct conversation
                if (joinLinkEnabled) {
                    groupInviteAsyncService.sendJoinLinkInvite(conversation, currentUserId, selfBlockedUserId);
                }
            }
            requestedIds.removeAll(selfBlockedInRequest);
        }
        if (requestedIds.isEmpty()) return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);

        Map<String, ConversationMember> existingMembersById = conversation.getMembers().stream()
                .collect(Collectors.toMap(ConversationMember::getUserId, m -> m, (a, b) -> a));
        Set<String> existingActiveMemberIds = conversation.getMembers().stream()
                .filter(helper::isActiveMember).map(ConversationMember::getUserId).collect(Collectors.toSet());
        requestedIds.removeAll(existingActiveMemberIds);
        if (requestedIds.isEmpty()) return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);

        // Check friend list — only friends can be added directly
        ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
        Set<String> friendIds = (currentUser != null && currentUser.getFriendIds() != null)
                ? currentUser.getFriendIds() : Collections.emptySet();
        Set<String> nonFriendIds = requestedIds.stream().filter(id -> !friendIds.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!nonFriendIds.isEmpty()) {
            var actorInfoForNonFriend = helper.fetchActorInfo(currentUserId);
            List<String> nonFriendTargetIds = new ArrayList<>(nonFriendIds);
            List<ChatUser> nonFriendUsers = chatUserRepository.findAllById(nonFriendIds);
            Map<String, String> nonFriendNameMap = nonFriendUsers.stream()
                    .collect(Collectors.toMap(ChatUser::getId, ChatUser::getFullName));
            Map<String, String> nonFriendAvatarMap = nonFriendUsers.stream()
                    .filter(u -> u.getAvatar() != null)
                    .collect(Collectors.toMap(ChatUser::getId, ChatUser::getAvatar));
            List<String> nonFriendNames = nonFriendTargetIds.stream()
                    .map(id -> nonFriendNameMap.getOrDefault(id, "Người dùng")).toList();
            List<String> nonFriendAvatars = nonFriendTargetIds.stream()
                    .map(id -> nonFriendAvatarMap.getOrDefault(id, "")).toList();

            systemMessageService.sendSystemMessage(conversationId, currentUserId,
                    actorInfoForNonFriend.name(), actorInfoForNonFriend.avatar(),
                    SystemActionType.ADD_MEMBERS_FAILED,
                    Map.of("targetIds", nonFriendTargetIds,
                            "payload", Map.of("targetNames", nonFriendNames, "targetAvatars", nonFriendAvatars,
                                    "failedCount", nonFriendIds.size())),
                    Set.of(currentUserId));
            requestedIds.removeAll(nonFriendIds);
        }
        if (requestedIds.isEmpty()) return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);

        List<ChatUser> users = chatUserRepository.findAllById(requestedIds);
        if (users.size() != requestedIds.size()) throw new AppException(ErrorCode.USER_NOT_FOUND);

        LocalDateTime now = LocalDateTime.now();
        requestedIds.forEach(id -> {
            ConversationMember existingMember = existingMembersById.get(id);
            if (existingMember != null) {
                existingMember.setActive(true);
                existingMember.setRemovedAt(null);
                existingMember.setRemovedBy(null);
                existingMember.setRole(MemberRole.MEMBER);
                existingMember.setJoinedAt(now.minusSeconds(1));
                existingMember.setJoinMethod(JoinMethod.ADDED_BY_MEMBER);
                existingMember.setAddedBy(currentUserId);
                return;
            }
            conversation.getMembers().add(
                    ConversationMember.builder().userId(id).role(MemberRole.MEMBER).joinedAt(now)
                    .joinMethod(JoinMethod.ADDED_BY_MEMBER).addedBy(currentUserId).build());
        });

        if (conversation.getUnreadCounts() == null) conversation.setUnreadCounts(new HashMap<>());
        requestedIds.forEach(id -> conversation.getUnreadCounts().putIfAbsent(id, 0));

        boolean canReadRecent = conversation.getSettings() == null || conversation.getSettings().isNewMembersCanReadRecent();
        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        requestedIds.forEach(id -> {
            if (canReadRecent) {
                conversation.getDeletedBefore().remove(id);
            } else {
                conversation.getDeletedBefore().put(id, now);
            }
        });

        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        Map<String, String> requestedNameMap = users.stream()
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getFullName));
        Map<String, String> requestedAvatarMap = users.stream()
                .filter(u -> u.getAvatar() != null)
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getAvatar));
        List<String> addedTargetIds = new ArrayList<>(requestedIds);
        List<String> addedTargetNames = addedTargetIds.stream()
                .map(id -> requestedNameMap.getOrDefault(id, "Người dùng")).toList();
        List<String> addedTargetAvatars = addedTargetIds.stream()
                .map(id -> requestedAvatarMap.getOrDefault(id, "")).toList();

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.ADD_MEMBERS,
                Map.of("targetIds", addedTargetIds,
                        "payload", Map.of("targetNames", addedTargetNames, "targetAvatars", addedTargetAvatars)));

        // Publish GroupMemberChangedEvent for each added member
        for (String memberId : requestedIds) {
            publishGroupMemberEvent(conversationId, memberId, GroupMemberChangedEvent.GroupMemberAction.JOINED);
        }

        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse removeMemberFromGroup(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);

        if (targetUserId == null || targetUserId.isBlank() || targetUserId.equals(currentUserId)) {
            throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_YOURSELF);
        }

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        ConversationMember target = helper.getMemberOrThrow(conversation, targetUserId);
        helper.assertOwnerOrAdmin(actor);
        helper.assertCanRemoveMember(actor, target);

        target.setActive(false);
        target.setRemovedAt(LocalDateTime.now());
        target.setRemovedBy(currentUserId);

        if (conversation.getUnreadCounts() != null) conversation.getUnreadCounts().remove(targetUserId);

        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        conversation.getDeletedBefore().put(targetUserId, LocalDateTime.now());

        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        var targetInfo = helper.fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.REMOVE_MEMBER,
                Map.of("targetIds", List.of(targetUserId),
                        "payload", Map.of("targetName", targetInfo.name(), "targetAvatar", targetInfo.avatar() != null ? targetInfo.avatar() : "")));

        // Publish GroupMemberChangedEvent for removed member
        publishGroupMemberEvent(conversationId, targetUserId, GroupMemberChangedEvent.GroupMemberAction.LEFT);

        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse promoteToAdmin(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        if (helper.resolveRole(actor) != MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        ConversationMember target = helper.getMemberOrThrow(conversation, targetUserId);
        MemberRole targetRole = helper.resolveRole(target);
        if (targetRole == MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_CANNOT_PROMOTE_OWNER);
        if (targetRole == MemberRole.ADMIN) throw new AppException(ErrorCode.CHAT_TARGET_ALREADY_ADMIN);

        target.setRole(MemberRole.ADMIN);
        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        var targetInfo = helper.fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.PROMOTE_ADMIN,
                Map.of("targetIds", List.of(targetUserId), "payload", Map.of("targetName", targetInfo.name())));

        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse demoteFromAdmin(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        if (helper.resolveRole(actor) != MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        ConversationMember target = helper.getMemberOrThrow(conversation, targetUserId);
        if (helper.resolveRole(target) != MemberRole.ADMIN) throw new AppException(ErrorCode.CHAT_TARGET_NOT_ADMIN);

        target.setRole(MemberRole.MEMBER);
        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        var targetInfo = helper.fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.DEMOTE_ADMIN,
                Map.of("targetIds", List.of(targetUserId), "payload", Map.of("targetName", targetInfo.name())));

        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse updateGroupName(String conversationId, String name) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);
        helper.assertSettingAllowed(conversation, currentUserId, GroupSettings::isMemberCanChangeInfo);

        String normalizedName = (name == null || name.isBlank()) ? null : name.strip();
        String oldName = conversation.getName();

        if (Objects.equals(oldName, normalizedName))
            return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);

        String displayOldName = (oldName == null || oldName.isBlank())
                ? fetchCurrentDynamicName(conversation, currentUserId) : oldName;

        conversation.setName(normalizedName);
        conversationRepository.save(conversation);

        String displayNewName = (normalizedName == null || normalizedName.isBlank())
                ? fetchCurrentDynamicName(conversation, currentUserId) : normalizedName;

        var actorInfo = helper.fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.UPDATE_NAME,
                Map.of("payload", Map.of("oldName", displayOldName, "newName", displayNewName)));

        return helper.broadcastAndRespond(conversation, currentUserId);
    }

    private String fetchCurrentDynamicName(Conversation conversation, String currentUserId) {
        Set<String> memberIds = conversation.getMembers().stream()
                .filter(helper::isActiveMember)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));
        return helper.getDynamicGroupName(conversation, currentUserId, userCache);
    }

    @Override
    public ConversationResponse updateGroupAvatar(String conversationId, MultipartFile file) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);
        helper.assertSettingAllowed(conversation, currentUserId, GroupSettings::isMemberCanChangeInfo);

        if (file != null && !file.isEmpty()) {
            String folder = "conversations/groups/" + conversationId + "/avatar";
            ApiResponse<FileUploadResponse> uploadResponse = fileServiceClient.upload(file, folder);
            if (uploadResponse != null && uploadResponse.data() != null) {
                String oldAvatarKey = conversation.getAvatar();
                String avatarKey = uploadResponse.data().key();

                conversation.setAvatar(avatarKey);

                var actorInfo = helper.fetchActorInfo(currentUserId);
                conversationRepository.save(conversation);

                if (oldAvatarKey != null && !oldAvatarKey.isBlank() && !oldAvatarKey.equals(avatarKey)) {
                    try {
                        fileServiceClient.delete(oldAvatarKey);
                        log.info("[Group] Deleted old avatar {} for conversation {}", oldAvatarKey, conversationId);
                    } catch (Exception e) {
                        log.warn("[Group] Failed to delete old avatar {} for conversation {}: {}",
                                oldAvatarKey, conversationId, e.getMessage());
                    }
                }

                systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                        SystemActionType.UPDATE_AVATAR, Map.of());

                return helper.broadcastAndRespond(conversation, currentUserId);
            }
        }

        helper.broadcastConversationUpdate(conversation);
        return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);
    }

    @Override
    public ConversationResponse updateGroupSettings(String conversationId, UpdateGroupSettingsRequest request) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        GroupSettings settings = conversation.getSettings();
        if (settings == null) {
            settings = GroupSettings.builder().build();
        }

        // Track which system-message-worthy settings changed
        boolean sendMessageChanged = false;
        boolean membershipApprovalChanged = false;
        boolean joinByLinkChanged = false;

        if (request.memberCanChangeInfo() != null) settings.setMemberCanChangeInfo(request.memberCanChangeInfo());
        if (request.memberCanPinMessages() != null) settings.setMemberCanPinMessages(request.memberCanPinMessages());
        if (request.memberCanCreateNotes() != null) settings.setMemberCanCreateNotes(request.memberCanCreateNotes());
        if (request.memberCanCreatePolls() != null) settings.setMemberCanCreatePolls(request.memberCanCreatePolls());
        if (request.memberCanSendMessages() != null) {
            sendMessageChanged = settings.isMemberCanSendMessages() != request.memberCanSendMessages();
            settings.setMemberCanSendMessages(request.memberCanSendMessages());
        }
        if (request.membershipApprovalEnabled() != null) {
            membershipApprovalChanged = settings.isMembershipApprovalEnabled() != request.membershipApprovalEnabled();
            settings.setMembershipApprovalEnabled(request.membershipApprovalEnabled());
        }
        if (request.highlightAdminMessages() != null) settings.setHighlightAdminMessages(request.highlightAdminMessages());
        if (request.newMembersCanReadRecent() != null) settings.setNewMembersCanReadRecent(request.newMembersCanReadRecent());
        if (request.joinByLinkEnabled() != null) {
            joinByLinkChanged = settings.isJoinByLinkEnabled() != request.joinByLinkEnabled();
            settings.setJoinByLinkEnabled(request.joinByLinkEnabled());
        }

        conversation.setSettings(settings);
        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);

        if (sendMessageChanged) {
            systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                    SystemActionType.UPDATE_SETTINGS,
                    Map.of("payload", Map.of("setting", "memberCanSendMessages", "value", settings.isMemberCanSendMessages())));
        }

        if (membershipApprovalChanged) {
            systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                    SystemActionType.UPDATE_SETTINGS,
                    Map.of("payload", Map.of("setting", "membershipApprovalEnabled", "value", settings.isMembershipApprovalEnabled())));
        }

        if (joinByLinkChanged && settings.isJoinByLinkEnabled()) {
            if (saved.getJoinLinkToken() == null) {
                String token = UUID.randomUUID().toString().replace("-", "");
                saved.setJoinLinkToken(token);
                saved = conversationRepository.save(saved);
            }
            systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                    SystemActionType.GENERATE_JOIN_LINK,
                    Map.of("payload", Map.of("token", saved.getJoinLinkToken())));
        }

        if (joinByLinkChanged && !settings.isJoinByLinkEnabled()) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(saved.getId())),
                    new Update().unset("joinLinkToken"),
                    Conversation.class
            );
            saved.setJoinLinkToken(null);
        }

        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public void disbandGroup(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        boolean isOwner = conversation.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(currentUserId)
                        && helper.isActiveMember(m) && m.getRole() == MemberRole.OWNER);
        if (!isOwner) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        conversation.setDisbanded(true);
        conversationRepository.save(conversation);
        messageRepository.deleteByConversationId(conversationId);

        // Publish LEFT events for all active members
        conversation.getMembers().stream()
                .filter(helper::isActiveMember)
                .map(ConversationMember::getUserId)
                .forEach(memberId -> publishGroupMemberEvent(conversationId, memberId, GroupMemberChangedEvent.GroupMemberAction.LEFT));

        var actorInfo = helper.fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.DISBAND_GROUP, Map.of());

        helper.broadcastConversationUpdate(conversation);
        log.info("[Group] Group {} has been disbanded by owner {}", conversationId, currentUserId);
    }

    @Override
    public void leaveGroup(String conversationId, LeaveGroupRequest request) {
        boolean silent = request.silent();
        String transferTo = request.transferTo();
        boolean blockReJoin = request.blockReJoin();

        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);

        ConversationMember currentMember = helper.getMemberOrThrow(conversation, currentUserId);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        ConversationHelper.ActorInfo transferTargetInfo = null;

        if (helper.resolveRole(currentMember) == MemberRole.OWNER) {
            if (transferTo == null || transferTo.isBlank()) {
                throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_OWNER);
            }
            ConversationMember target = helper.getMemberOrThrow(conversation, transferTo);
            if (!helper.isActiveMember(target)) throw new AppException(ErrorCode.CHAT_TARGET_NOT_MEMBER);
            if (target.getUserId().equals(currentUserId)) throw new AppException(ErrorCode.CHAT_CANNOT_TRANSFER_TO_SELF);

            currentMember.setRole(MemberRole.MEMBER);
            target.setRole(MemberRole.OWNER);
            transferTargetInfo = helper.fetchActorInfo(transferTo);
        }

        currentMember.setActive(false);
        currentMember.setRemovedAt(LocalDateTime.now());
        if (conversation.getUnreadCounts() != null) conversation.getUnreadCounts().remove(currentUserId);

        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        conversation.getDeletedBefore().put(currentUserId, LocalDateTime.now());

        if (blockReJoin) {
            if (conversation.getSelfBlockedUserIds() == null) conversation.setSelfBlockedUserIds(new HashSet<>());
            conversation.getSelfBlockedUserIds().add(currentUserId);
        }

        conversationRepository.save(conversation);

        if (!silent) {
            Set<String> otherMemberIds = conversation.getMembers().stream()
                    .filter(helper::isActiveMember)
                    .map(ConversationMember::getUserId)
                    .collect(Collectors.toSet());
            systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                    SystemActionType.LEAVE_GROUP, Map.of(), otherMemberIds);
        } else {
            Set<String> adminOwnerIds = conversation.getMembers().stream()
                    .filter(helper::isActiveMember)
                    .filter(m -> {
                        MemberRole role = helper.resolveRole(m);
                        return role == MemberRole.OWNER || role == MemberRole.ADMIN;
                    })
                    .map(ConversationMember::getUserId).collect(Collectors.toSet());
            systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                    SystemActionType.LEAVE_GROUP, Map.of(), adminOwnerIds);
        }

        if (transferTargetInfo != null) {
            systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                    SystemActionType.TRANSFER_OWNER,
                    Map.of("targetIds", List.of(transferTo), "payload", Map.of("targetName", transferTargetInfo.name())));
        }

        // Publish GroupMemberChangedEvent for user leaving
        publishGroupMemberEvent(conversationId, currentUserId, GroupMemberChangedEvent.GroupMemberAction.LEFT);

        helper.broadcastConversationUpdate(conversation);
        log.info("[Group] User {} left group {} (silent={})", currentUserId, conversationId, silent);
    }

    @Override
    public Map<String, List<SearchMemberResponse>> getFriendsDirectory(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        String baseUrl = helper.getBaseUrl();
        final Set<String> memberIds = getActiveMemberIds(conversationId);

        ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
        if (currentUser == null || currentUser.getFriendIds().isEmpty()) return new TreeMap<>();

        List<ChatUser> friends = chatUserRepository.findAllById(currentUser.getFriendIds());

        return friends.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(u -> SearchMemberResponse.builder()
                        .userId(u.getId()).fullName(u.getFullName())
                        .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                        .isAlreadyMember(memberIds.contains(u.getId())).build())
                .sorted(Comparator.comparing(u -> normalizeForSort(u.fullName())))
                .collect(Collectors.groupingBy(
                        u -> getNormalizedFirstLetter(u.fullName()), TreeMap::new, Collectors.toList()));
    }

    @Override
    public PageResponse<List<SearchMemberResponse>> searchMembersToAdd(String conversationId, String query, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);

        final Set<String> memberIds = getActiveMemberIds(conversationId);

        Set<ChatUser> candidates = new LinkedHashSet<>();
        String[] tokens = query.trim().split("\\s+");
        List<String> phoneTokens = new ArrayList<>();
        StringBuilder nameQueryBuilder = new StringBuilder();

        for (String token : tokens) {
            Optional<String> normalizedPhone = PhoneUtil.normalizeVnPhone(token);
            if (normalizedPhone.isPresent()) {
                phoneTokens.add(normalizedPhone.get());
            } else {
                if (!nameQueryBuilder.isEmpty()) nameQueryBuilder.append(" ");
                nameQueryBuilder.append(token);
            }
        }

        if (!phoneTokens.isEmpty()) {
            candidates.addAll(chatUserRepository.findAllByPhoneNumberIn(phoneTokens).stream()
                    .filter(u -> !u.getId().equals(currentUserId))
                    .toList());
        }

        String nameQuery = nameQueryBuilder.toString();
        if (!nameQuery.isBlank()) {
            ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
            if (currentUser != null && !currentUser.getFriendIds().isEmpty()) {
                Set<String> friendIds = new HashSet<>(currentUser.getFriendIds());
                friendIds.remove(currentUserId);
                // For simplicity in pagination with combined results, we'll fetch a reasonable amount of name matches
                // In a production app, you might want more complex cross-index pagination
                candidates.addAll(chatUserRepository.findByIdInAndFullNameContainingIgnoreCase(friendIds, nameQuery, pageable).getContent());
            }
        }

        List<ChatUser> candidateList = new ArrayList<>(candidates);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), candidateList.size());
        List<ChatUser> pagedCandidates = (start < candidateList.size()) ? candidateList.subList(start, end) : Collections.emptyList();
        Page<ChatUser> candidatesPage = new PageImpl<>(pagedCandidates, pageable, candidateList.size());

        String baseUrl = helper.getBaseUrl();
        return PageResponse.fromPage(candidatesPage, u -> {
            String phoneNumber = (u.getPhoneNumber() != null && phoneTokens.contains(u.getPhoneNumber())) 
                    ? u.getPhoneNumber() : null;
            
            return SearchMemberResponse.builder()
                .userId(u.getId()).fullName(u.getFullName())
                .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                .phoneNumber(phoneNumber)
                .isAlreadyMember(memberIds.contains(u.getId())).build();
        });
    }

    @Override
    public PageResponse<List<GroupMemberListItemResponse>> getGroupMembers(String conversationId, String query, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);

        Set<String> friendIds = chatUserRepository.findById(currentUserId)
                .map(ChatUser::getFriendIds).map(HashSet::new).orElseGet(HashSet::new);
        friendIds.remove(currentUserId);

        String normalizedQuery = query == null ? "" : query.trim();
        boolean hasQuery = !normalizedQuery.isBlank();
        String baseUrl = helper.getBaseUrl();
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
                                new Document("$eq", List.of("$_id",
                                        new Document("$convert", new Document("input", "$$memberUserId")
                                                .append("to", "objectId").append("onError", null).append("onNull", null))))
                        ))))))
                .append("as", "user")));
        commonStages.add(Aggregation.unwind("user", true));

        if (hasQuery) {
            String escaped = Pattern.quote(normalizedQuery);
            commonStages.add(Aggregation.match(new Criteria().orOperator(
                    Criteria.where("user.fullName").regex(escaped, "i"),
                    Criteria.where("user.phoneNumber").regex(escaped, "i"))));
        }

        Document sortBucketExpr = new Document("$switch", new Document("branches", List.of(
                new Document("case", new Document("$eq", List.of("$members.role", "OWNER"))).append("then", 0),
                new Document("case", new Document("$eq", List.of("$members.role", "ADMIN"))).append("then", 1)
        )).append("default", new Document("$cond", List.of(
                new Document("$in", List.of("$members.userId", friendIdList)), 3, 2))));

        Document addSortFieldsDoc = new Document("sortBucket", sortBucketExpr)
                .append("nameSort", new Document("$toLower", new Document("$ifNull", List.of("$user.fullName", ""))));
        Document projectFieldsDoc = new Document()
                .append("_id", 0).append("userId", "$members.userId")
                .append("fullName", new Document("$ifNull", List.of("$user.fullName", "Người dùng")))
                .append("avatar", "$user.avatar").append("phoneNumber", "$user.phoneNumber")
                .append("role", "$members.role").append("joinedAt", "$members.joinedAt")
                .append("joinMethod", "$members.joinMethod").append("addedBy", "$members.addedBy");

        Document facetDoc = new Document("$facet", new Document()
                .append("metadata", List.of(new Document("$count", "totalItems")))
                .append("data", List.of(
                        new Document("$addFields", addSortFieldsDoc),
                        new Document("$sort", new Document("sortBucket", 1).append("nameSort", 1).append("members.joinedAt", 1)),
                        new Document("$skip", pageable.getOffset()),
                        new Document("$limit", pageable.getPageSize()),
                        new Document("$project", projectFieldsDoc))));

        List<AggregationOperation> pipeline = new ArrayList<>(commonStages);
        pipeline.add(context -> facetDoc);

        AggregationResults<Document> aggregated = mongoTemplate.aggregate(
                Aggregation.newAggregation(pipeline), "conversations", Document.class);

        Document root = aggregated.getUniqueMappedResult();
        List<Document> metadata = extractDocumentList(root != null ? root.get("metadata") : null);
        List<Document> dataDocs = extractDocumentList(root != null ? root.get("data") : null);
        int totalItems = !metadata.isEmpty() ? metadata.getFirst().getInteger("totalItems", 0) : 0;

        // Build a map of userId -> fullName for addedBy lookup
        Set<String> addedByIds = dataDocs.stream()
                .map(doc -> doc.getString("addedBy")).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> addedByNameMap = addedByIds.isEmpty() ? Collections.emptyMap()
                : chatUserRepository.findAllById(addedByIds).stream()
                    .collect(Collectors.toMap(ChatUser::getId, ChatUser::getFullName));

        List<GroupMemberListItemResponse> pageData = dataDocs.stream().map(doc -> {
            String userId = doc.getString("userId");
            String fullName = doc.getString("fullName") != null ? doc.getString("fullName") : "Người dùng";
            String avatar = doc.getString("avatar");
            String phoneNumber = doc.getString("phoneNumber");
            String roleRaw = doc.getString("role");
            MemberRole role = roleRaw != null ? MemberRole.valueOf(roleRaw) : MemberRole.MEMBER;
            boolean isCurrentUser = currentUserId.equals(userId);
            boolean isFriend = friendIds.contains(userId);
            String joinMethod = doc.getString("joinMethod");
            String addedById = doc.getString("addedBy");
            String addedByName = addedById != null ? addedByNameMap.get(addedById) : null;

            return GroupMemberListItemResponse.builder()
                    .userId(userId).fullName(fullName)
                    .avatar(avatar != null ? baseUrl + avatar : null)
                    .phoneNumber(phoneNumber).role(role)
                    .joinedAt(helper.toOffsetFromMongo(doc.get("joinedAt")))
                    .isCurrentUser(isCurrentUser).isFriend(isFriend)
                    .joinMethod(joinMethod).addedBy(addedById).addedByName(addedByName).build();
        }).toList();

        return PageResponse.<List<GroupMemberListItemResponse>>builder()
                .data(pageData).page(pageable.getPageNumber())
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageable.getPageSize()))
                .limit(pageable.getPageSize()).totalItems(totalItems).build();
    }

    @Override
    public PageResponse<List<AdminMemberResponse>> getGroupAdmins(String conversationId, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);

        String baseUrl = helper.getBaseUrl();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(Criteria.where("_id").is(conversationId).and("isGroup").is(true)));
        pipeline.add(Aggregation.unwind("members"));
        pipeline.add(Aggregation.match(Criteria.where("members.active").ne(false)
                .and("members.role").in("OWNER", "ADMIN")));
        pipeline.add(context -> new Document("$lookup", new Document()
                .append("from", "chat_users")
                .append("let", new Document("memberUserId", "$members.userId"))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$or", List.of(
                                new Document("$eq", List.of("$_id", "$$memberUserId")),
                                new Document("$eq", List.of("$_id",
                                        new Document("$convert", new Document("input", "$$memberUserId")
                                                .append("to", "objectId").append("onError", null).append("onNull", null))))
                        ))))))
                .append("as", "user")));
        pipeline.add(Aggregation.unwind("user", true));

        Document sortBucketExpr = new Document("$cond", List.of(
                new Document("$eq", List.of("$members.role", "OWNER")), 0, 1));
        Document addSortFieldsDoc = new Document("sortBucket", sortBucketExpr)
                .append("nameSort", new Document("$toLower", new Document("$ifNull", List.of("$user.fullName", ""))));
        Document projectFieldsDoc = new Document()
                .append("_id", 0).append("userId", "$members.userId")
                .append("fullName", new Document("$ifNull", List.of("$user.fullName", "Người dùng")))
                .append("avatar", "$user.avatar")
                .append("role", "$members.role");

        Document facetDoc = new Document("$facet", new Document()
                .append("metadata", List.of(new Document("$count", "totalItems")))
                .append("data", List.of(
                        new Document("$addFields", addSortFieldsDoc),
                        new Document("$sort", new Document("sortBucket", 1).append("nameSort", 1)),
                        new Document("$skip", pageable.getOffset()),
                        new Document("$limit", pageable.getPageSize()),
                        new Document("$project", projectFieldsDoc))));

        pipeline.add(context -> facetDoc);

        AggregationResults<Document> aggregated = mongoTemplate.aggregate(
                Aggregation.newAggregation(pipeline), "conversations", Document.class);

        Document root = aggregated.getUniqueMappedResult();
        List<Document> metadata = extractDocumentList(root != null ? root.get("metadata") : null);
        List<Document> dataDocs = extractDocumentList(root != null ? root.get("data") : null);
        int totalItems = !metadata.isEmpty() ? metadata.getFirst().getInteger("totalItems", 0) : 0;

        List<AdminMemberResponse> pageData = dataDocs.stream().map(doc -> {
            String avatar = doc.getString("avatar");
            String roleRaw = doc.getString("role");
            return AdminMemberResponse.builder()
                    .userId(doc.getString("userId"))
                    .fullName(doc.getString("fullName") != null ? doc.getString("fullName") : "Người dùng")
                    .avatar(avatar != null ? baseUrl + avatar : null)
                    .role(roleRaw != null ? MemberRole.valueOf(roleRaw) : MemberRole.ADMIN)
                    .build();
        }).toList();

        return PageResponse.<List<AdminMemberResponse>>builder()
                .data(pageData).page(pageable.getPageNumber())
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageable.getPageSize()))
                .limit(pageable.getPageSize()).totalItems(totalItems).build();
    }

    @Override
    public PageResponse<List<AdminMemberResponse>> getAdminCandidates(String conversationId, String query, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        if (helper.resolveRole(actor) != MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        String normalizedQuery = query == null ? "" : query.trim();
        boolean hasQuery = !normalizedQuery.isBlank();
        String baseUrl = helper.getBaseUrl();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(Criteria.where("_id").is(conversationId).and("isGroup").is(true)));
        pipeline.add(Aggregation.unwind("members"));
        pipeline.add(Aggregation.match(Criteria.where("members.active").ne(false)
                .and("members.role").ne("OWNER")));
        pipeline.add(context -> new Document("$lookup", new Document()
                .append("from", "chat_users")
                .append("let", new Document("memberUserId", "$members.userId"))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$or", List.of(
                                new Document("$eq", List.of("$_id", "$$memberUserId")),
                                new Document("$eq", List.of("$_id",
                                        new Document("$convert", new Document("input", "$$memberUserId")
                                                .append("to", "objectId").append("onError", null).append("onNull", null))))
                        ))))))
                .append("as", "user")));
        pipeline.add(Aggregation.unwind("user", true));

        if (hasQuery) {
            String escaped = Pattern.quote(normalizedQuery);
            pipeline.add(Aggregation.match(new Criteria().orOperator(
                    Criteria.where("user.fullName").regex(escaped, "i"),
                    Criteria.where("user.phoneNumber").regex(escaped, "i"))));
        }

        // Admins first (checked), then regular members (unchecked), both sorted by name ASC
        Document sortBucketExpr = new Document("$cond", List.of(
                new Document("$eq", List.of("$members.role", "ADMIN")), 0, 1));
        Document addSortFieldsDoc = new Document("sortBucket", sortBucketExpr)
                .append("nameSort", new Document("$toLower", new Document("$ifNull", List.of("$user.fullName", ""))));
        Document projectFieldsDoc = new Document()
                .append("_id", 0).append("userId", "$members.userId")
                .append("fullName", new Document("$ifNull", List.of("$user.fullName", "Người dùng")))
                .append("avatar", "$user.avatar")
                .append("role", "$members.role");

        Document facetDoc = new Document("$facet", new Document()
                .append("metadata", List.of(new Document("$count", "totalItems")))
                .append("data", List.of(
                        new Document("$addFields", addSortFieldsDoc),
                        new Document("$sort", new Document("sortBucket", 1).append("nameSort", 1)),
                        new Document("$skip", pageable.getOffset()),
                        new Document("$limit", pageable.getPageSize()),
                        new Document("$project", projectFieldsDoc))));

        pipeline.add(context -> facetDoc);

        AggregationResults<Document> aggregated = mongoTemplate.aggregate(
                Aggregation.newAggregation(pipeline), "conversations", Document.class);

        Document root = aggregated.getUniqueMappedResult();
        List<Document> metadata = extractDocumentList(root != null ? root.get("metadata") : null);
        List<Document> dataDocs = extractDocumentList(root != null ? root.get("data") : null);
        int totalItems = !metadata.isEmpty() ? metadata.getFirst().getInteger("totalItems", 0) : 0;

        List<AdminMemberResponse> pageData = dataDocs.stream().map(doc -> {
            String avatar = doc.getString("avatar");
            String roleRaw = doc.getString("role");
            return AdminMemberResponse.builder()
                    .userId(doc.getString("userId"))
                    .fullName(doc.getString("fullName") != null ? doc.getString("fullName") : "Người dùng")
                    .avatar(avatar != null ? baseUrl + avatar : null)
                    .role(roleRaw != null ? MemberRole.valueOf(roleRaw) : MemberRole.MEMBER)
                    .build();
        }).toList();

        return PageResponse.<List<AdminMemberResponse>>builder()
                .data(pageData).page(pageable.getPageNumber())
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageable.getPageSize()))
                .limit(pageable.getPageSize()).totalItems(totalItems).build();
    }

    @Override
    public ConversationResponse transferOwnership(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        if (helper.resolveRole(actor) != MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        if (targetUserId == null || targetUserId.isBlank() || targetUserId.equals(currentUserId)) {
            throw new AppException(ErrorCode.CHAT_CANNOT_TRANSFER_TO_SELF);
        }

        ConversationMember target = helper.getMemberOrThrow(conversation, targetUserId);

        actor.setRole(MemberRole.MEMBER);
        target.setRole(MemberRole.OWNER);
        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        var targetInfo = helper.fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.TRANSFER_OWNER,
                Map.of("targetIds", List.of(targetUserId),
                        "payload", Map.of("targetName", targetInfo.name())));

        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public String generateJoinLink(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        if (conversation.getJoinLinkToken() != null) {
            return conversation.getJoinLinkToken();
        }

        return buildJoinLinkToken(conversation, conversationId, currentUserId,
                SystemActionType.GENERATE_JOIN_LINK, "generated");
    }

    @Override
    public String refreshJoinLink(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        return buildJoinLinkToken(conversation, conversationId, currentUserId,
                SystemActionType.REFRESH_JOIN_LINK, "refreshed");
    }

    private String buildJoinLinkToken(Conversation conversation, String conversationId,
                                      String currentUserId, SystemActionType action, String logVerb) {
        String token = UUID.randomUUID().toString().replace("-", "");
        conversation.setJoinLinkToken(token);

        GroupSettings settings = conversation.getSettings();
        if (settings == null) {
            settings = GroupSettings.builder().build();
            conversation.setSettings(settings);
        }
        settings.setJoinByLinkEnabled(true);

        conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                action, Map.of("payload", Map.of("token", token)));

        helper.broadcastConversationUpdate(conversation);
        log.info("[Group] Join link {} for conversation {} by user {}", logVerb, conversationId, currentUserId);
        return token;
    }

    private Optional<Conversation> findDuplicateGroup(String groupName, Set<String> allMemberIds) {
        Criteria criteria = Criteria.where("isGroup").is(true)
                .and("isDisbanded").is(false)
                .and("name").is(groupName);

        // Each member must be an active member in the group
        List<Criteria> memberCriteria = allMemberIds.stream()
                .map(id -> Criteria.where("members")
                        .elemMatch(Criteria.where("userId").is(id).and("active").ne(false)))
                .toList();
        criteria = criteria.andOperator(memberCriteria.toArray(new Criteria[0]));

        Query query = Query.query(criteria);
        List<Conversation> candidates = mongoTemplate.find(query, Conversation.class);

        // Filter to exact member set match (not superset)
        return candidates.stream()
                .filter(c -> {
                    Set<String> activeMemberIds = c.getMembers().stream()
                            .filter(helper::isActiveMember)
                            .map(ConversationMember::getUserId)
                            .collect(Collectors.toSet());
                    return activeMemberIds.equals(allMemberIds);
                })
                .findFirst();
    }

    private Set<String> getActiveMemberIds(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return new HashSet<>();
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .getMembers().stream().filter(helper::isActiveMember)
                .map(ConversationMember::getUserId).collect(Collectors.toSet());
    }

    private Set<String> getBlockedUserIds(Conversation conversation) {
        return conversation.getBlockedUserIds() != null ? conversation.getBlockedUserIds() : Collections.emptySet();
    }

    @Override
    public ConversationResponse blockMemberFromGroup(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        if (targetUserId == null || targetUserId.isBlank() || targetUserId.equals(currentUserId)) {
            throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_YOURSELF);
        }

        ConversationMember target = helper.getMemberOrThrow(conversation, targetUserId);
        MemberRole targetRole = helper.resolveRole(target);
        if (targetRole == MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_CANNOT_BLOCK_OWNER);
        helper.assertCanRemoveMember(actor, target);

        if (conversation.getBlockedUserIds() == null) conversation.setBlockedUserIds(new HashSet<>());
        if (conversation.getBlockedUserIds().contains(targetUserId)) {
            throw new AppException(ErrorCode.CHAT_USER_ALREADY_BLOCKED_FROM_GROUP);
        }

        // Remove member from group
        target.setActive(false);
        target.setRemovedAt(LocalDateTime.now());
        target.setRemovedBy(currentUserId);
        if (conversation.getUnreadCounts() != null) conversation.getUnreadCounts().remove(targetUserId);

        // Set deletedBefore so conversation won't reappear on refetch
        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        conversation.getDeletedBefore().put(targetUserId, LocalDateTime.now());

        // Add to block list
        conversation.getBlockedUserIds().add(targetUserId);

        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        var targetInfo = helper.fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.BLOCK_MEMBER,
                Map.of("targetIds", List.of(targetUserId),
                        "payload", Map.of("targetName", targetInfo.name(), "targetAvatar", targetInfo.avatar() != null ? targetInfo.avatar() : "")));

        log.info("[Group] User {} blocked member {} from group {}", currentUserId, targetUserId, conversationId);
        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse unblockMemberFromGroup(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        if (conversation.getBlockedUserIds() == null || !conversation.getBlockedUserIds().contains(targetUserId)) {
            throw new AppException(ErrorCode.CHAT_USER_NOT_BLOCKED_FROM_GROUP);
        }

        conversation.getBlockedUserIds().remove(targetUserId);
        Conversation saved = conversationRepository.save(conversation);

        log.info("[Group] User {} unblocked member {} from group {}", currentUserId, targetUserId, conversationId);
        return helper.broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public PageResponse<List<SearchMemberResponse>> getBlockedMembers(String conversationId, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        Set<String> blockedIds = getBlockedUserIds(conversation);
        if (blockedIds.isEmpty()) {
            return PageResponse.<List<SearchMemberResponse>>builder()
                    .data(List.of()).page(page).limit(size).totalItems(0).totalPages(0).build();
        }

        List<ChatUser> blockedUsers = chatUserRepository.findAllById(blockedIds);
        String baseUrl = helper.getBaseUrl();

        List<SearchMemberResponse> all = blockedUsers.stream()
                .map(u -> SearchMemberResponse.builder()
                        .userId(u.getId()).fullName(u.getFullName())
                        .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                        .isAlreadyMember(false).build())
                .sorted(Comparator.comparing(r -> r.fullName() != null ? r.fullName() : ""))
                .toList();

        int start = Math.min(page * size, all.size());
        int end = Math.min(start + size, all.size());
        List<SearchMemberResponse> pageData = all.subList(start, end);

        return PageResponse.<List<SearchMemberResponse>>builder()
                .data(pageData).page(page).limit(size)
                .totalItems(all.size())
                .totalPages(all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / size))
                .build();
    }

    @Override
    public PageResponse<List<SearchMemberResponse>> getBlockCandidates(String conversationId, String query, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        Set<String> blockedIds = getBlockedUserIds(conversation);
        String normalizedQuery = query == null ? "" : query.trim();
        boolean hasQuery = !normalizedQuery.isBlank();
        String baseUrl = helper.getBaseUrl();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));

        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(Criteria.where("_id").is(conversationId).and("isGroup").is(true)));
        pipeline.add(Aggregation.unwind("members"));

        Criteria memberCriteria = Criteria.where("members.active").ne(false)
                .and("members.role").is("MEMBER");
        if (!blockedIds.isEmpty()) {
            memberCriteria = memberCriteria.and("members.userId").nin(blockedIds);
        }
        pipeline.add(Aggregation.match(memberCriteria));

        pipeline.add(context -> new Document("$lookup", new Document()
                .append("from", "chat_users")
                .append("let", new Document("memberUserId", "$members.userId"))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr", new Document("$or", List.of(
                                new Document("$eq", List.of("$_id", "$$memberUserId")),
                                new Document("$eq", List.of("$_id",
                                        new Document("$convert", new Document("input", "$$memberUserId")
                                                .append("to", "objectId").append("onError", null).append("onNull", null))))
                        ))))))
                .append("as", "user")));
        pipeline.add(Aggregation.unwind("user", true));

        if (hasQuery) {
            String escaped = Pattern.quote(normalizedQuery);
            pipeline.add(Aggregation.match(new Criteria().orOperator(
                    Criteria.where("user.fullName").regex(escaped, "i"),
                    Criteria.where("user.phoneNumber").regex(escaped, "i"))));
        }

        Document addSortFieldsDoc = new Document("nameSort",
                new Document("$toLower", new Document("$ifNull", List.of("$user.fullName", ""))));
        Document projectFieldsDoc = new Document()
                .append("_id", 0).append("userId", "$members.userId")
                .append("fullName", new Document("$ifNull", List.of("$user.fullName", "Người dùng")))
                .append("avatar", "$user.avatar");

        Document facetDoc = new Document("$facet", new Document()
                .append("metadata", List.of(new Document("$count", "totalItems")))
                .append("data", List.of(
                        new Document("$addFields", addSortFieldsDoc),
                        new Document("$sort", new Document("nameSort", 1)),
                        new Document("$skip", pageable.getOffset()),
                        new Document("$limit", pageable.getPageSize()),
                        new Document("$project", projectFieldsDoc))));

        pipeline.add(context -> facetDoc);

        AggregationResults<Document> aggregated = mongoTemplate.aggregate(
                Aggregation.newAggregation(pipeline), "conversations", Document.class);

        Document root = aggregated.getUniqueMappedResult();
        List<Document> metadata = extractDocumentList(root != null ? root.get("metadata") : null);
        List<Document> dataDocs = extractDocumentList(root != null ? root.get("data") : null);
        int totalItems = !metadata.isEmpty() ? metadata.getFirst().getInteger("totalItems", 0) : 0;

        List<SearchMemberResponse> pageData = dataDocs.stream().map(doc -> {
            String avatar = doc.getString("avatar");
            return SearchMemberResponse.builder()
                    .userId(doc.getString("userId"))
                    .fullName(doc.getString("fullName") != null ? doc.getString("fullName") : "Người dùng")
                    .avatar(avatar != null ? baseUrl + avatar : null)
                    .isAlreadyMember(false)
                    .build();
        }).toList();

        return PageResponse.<List<SearchMemberResponse>>builder()
                .data(pageData).page(pageable.getPageNumber())
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageable.getPageSize()))
                .limit(pageable.getPageSize()).totalItems(totalItems).build();
    }

    @Override
    public PageResponse<List<ConversationResponse>> getMyGroupConversations(String query, String sort, String filter, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();

        Criteria memberMatch = Criteria.where("userId").is(currentUserId).and("active").ne(false);
        if ("owner".equals(filter)) {
            memberMatch = memberMatch.and("role").is("OWNER");
        }
        Criteria criteria = Criteria.where("isGroup").is(true)
                .and("isDisbanded").ne(true)
                .and("members").elemMatch(memberMatch);

        if (query != null && !query.isBlank()) {
            criteria = criteria.and("name").regex(Pattern.quote(query.trim()), "i");
        }

        Sort sortObj = switch (sort == null ? "" : sort) {
            case "name_asc" -> Sort.by(Sort.Direction.ASC, "name");
            case "name_desc" -> Sort.by(Sort.Direction.DESC, "name");
            case "activity_oldest" -> Sort.by(Sort.Direction.ASC, "lastMessage.timestamp");
            default -> Sort.by(Sort.Direction.DESC, "lastMessage.timestamp");
        };

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), sortObj);
        Query mongoQuery = new Query(criteria).with(pageable);
        long total = mongoTemplate.count(new Query(criteria), Conversation.class);
        List<Conversation> conversations = mongoTemplate.find(mongoQuery, Conversation.class);

        if (conversations.isEmpty()) {
            return PageResponse.<List<ConversationResponse>>builder()
                    .data(Collections.emptyList()).page(pageable.getPageNumber())
                    .totalPages(0).limit(pageable.getPageSize()).totalItems(0).build();
        }

        Set<String> allUserIds = new HashSet<>();
        conversations.forEach(room -> {
            room.getMembers().forEach(m -> allUserIds.add(m.getUserId()));
            if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
                allUserIds.add(room.getLastMessage().getSenderId());
            }
        });

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));
        String baseUrl = helper.getBaseUrl();
        boolean viewerCanSee = helper.canViewerSeeStatus(currentUserId, userCache);

        List<ConversationResponse> responses = conversations.stream()
                .map(room -> helper.buildConversationResponse(room, null, currentUserId, userCache, baseUrl, viewerCanSee, null))
                .toList();

        int totalPages = (int) Math.ceil((double) total / pageable.getPageSize());
        return PageResponse.<List<ConversationResponse>>builder()
                .data(responses).page(pageable.getPageNumber())
                .totalPages(totalPages).limit(pageable.getPageSize()).totalItems((int) total).build();
    }

    private void publishGroupMemberEvent(String groupId, String userId, GroupMemberChangedEvent.GroupMemberAction action) {
        try {
            GroupMemberChangedEvent event = GroupMemberChangedEvent.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .action(action)
                    .timestamp(System.currentTimeMillis())
                    .build();
            outboxEventPublisher.saveAndPublish(groupId, "Group", EventType.GROUP_MEMBER_CHANGED, event);
        } catch (Exception e) {
            log.error("[Group] Failed to publish GroupMemberChangedEvent: groupId={}, userId={}, action={}",
                    groupId, userId, action, e);
        }
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

    private List<Document> extractDocumentList(Object value) {
        if (!(value instanceof List<?> rawList)) return List.of();
        List<Document> documents = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Document document) documents.add(document);
        }
        return documents;
    }
}
