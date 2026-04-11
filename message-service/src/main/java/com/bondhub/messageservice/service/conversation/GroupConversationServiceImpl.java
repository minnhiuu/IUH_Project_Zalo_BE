package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.request.UpdateGroupSettingsRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.GroupMemberListItemResponse;
import com.bondhub.messageservice.dto.response.JoinGroupPreviewResponse;
import com.bondhub.messageservice.dto.response.JoinRequestResponse;
import com.bondhub.messageservice.dto.response.SearchMemberResponse;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.GroupSettings;
import com.bondhub.messageservice.model.JoinRequest;
import com.bondhub.messageservice.model.LastMessageInfo;
import com.bondhub.messageservice.model.enums.JoinRequestStatus;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.JoinRequestRepository;
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

    private record ActorInfo(String name, String avatar) {
        static ActorInfo of(ChatUser user, String fallbackName) {
            return user != null
                    ? new ActorInfo(user.getFullName(), user.getAvatar())
                    : new ActorInfo(fallbackName, null);
        }
    }

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final MessageRepository messageRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final MongoTemplate mongoTemplate;
    private final SystemMessageService systemMessageService;
    private final FileServiceClient fileServiceClient;
    private final ConversationHelper helper;

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

        LocalDateTime now = LocalDateTime.now();

        Set<ConversationMember> members = new HashSet<>();
        members.add(ConversationMember.builder()
                .userId(currentUserId).role(MemberRole.OWNER).joinedAt(now).build());
        memberIds.forEach(id -> members.add(
                ConversationMember.builder().userId(id).role(MemberRole.MEMBER).joinedAt(now).build()));

        Map<String, Integer> unreadCounts = new HashMap<>();
        members.forEach(m -> unreadCounts.put(m.getUserId(), 0));

        Conversation conversation = Conversation.builder()
                .name(groupName).avatar(avatarUrl).isGroup(true)
                .members(members).unreadCounts(unreadCounts)
                .settings(GroupSettings.builder().build())
                .lastMessage(LastMessageInfo.builder().timestamp(now).build())
                .build();

        Conversation saved = conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        Map<String, String> userNameMap = users.stream()
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getFullName));
        Map<String, String> userAvatarMap = users.stream()
                .filter(u -> u.getAvatar() != null)
                .collect(Collectors.toMap(ChatUser::getId, ChatUser::getAvatar));
        List<String> createTargetIds = new ArrayList<>(memberIds);
        List<String> createTargetNames = createTargetIds.stream()
                .map(id -> userNameMap.getOrDefault(id, "Người dùng")).toList();
        List<String> createTargetAvatars = createTargetIds.stream()
                .map(id -> userAvatarMap.getOrDefault(id, "")).toList();

        systemMessageService.sendSystemMessage(saved.getId(), currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.CREATE_GROUP,
                Map.of("targetIds", createTargetIds,
                        "payload", Map.of("targetNames", createTargetNames, "targetAvatars", createTargetAvatars)));

        log.info("[Group] Created group {} by user {} with {} members", saved.getId(), currentUserId, saved.getMembers().size());
        return broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse addMembersToGroup(String conversationId, List<String> memberIds) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);

        Set<String> requestedIds = new LinkedHashSet<>(memberIds != null ? memberIds : Collections.emptyList());
        requestedIds.remove(currentUserId);
        if (requestedIds.isEmpty()) return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);

        Map<String, ConversationMember> existingMembersById = conversation.getMembers().stream()
                .collect(Collectors.toMap(ConversationMember::getUserId, m -> m, (a, b) -> a));
        Set<String> existingActiveMemberIds = conversation.getMembers().stream()
                .filter(helper::isActiveMember).map(ConversationMember::getUserId).collect(Collectors.toSet());
        requestedIds.removeAll(existingActiveMemberIds);
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
                return;
            }
            conversation.getMembers().add(
                    ConversationMember.builder().userId(id).role(MemberRole.MEMBER).joinedAt(now).build());
        });

        if (conversation.getUnreadCounts() == null) conversation.setUnreadCounts(new HashMap<>());
        requestedIds.forEach(id -> conversation.getUnreadCounts().putIfAbsent(id, 0));

        Conversation saved = conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
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

        return broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse removeMemberFromGroup(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
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

        Conversation saved = conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        ActorInfo targetInfo = fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.REMOVE_MEMBER,
                Map.of("targetIds", List.of(targetUserId),
                        "payload", Map.of("targetName", targetInfo.name(), "targetAvatar", targetInfo.avatar() != null ? targetInfo.avatar() : "")));

        return broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse promoteToAdmin(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        if (helper.resolveRole(actor) != MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        ConversationMember target = helper.getMemberOrThrow(conversation, targetUserId);
        MemberRole targetRole = helper.resolveRole(target);
        if (targetRole == MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_CANNOT_PROMOTE_OWNER);
        if (targetRole == MemberRole.ADMIN) throw new AppException(ErrorCode.CHAT_TARGET_ALREADY_ADMIN);

        target.setRole(MemberRole.ADMIN);
        Conversation saved = conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        ActorInfo targetInfo = fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.PROMOTE_ADMIN,
                Map.of("targetIds", List.of(targetUserId), "payload", Map.of("targetName", targetInfo.name())));

        return broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse demoteFromAdmin(String conversationId, String targetUserId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);

        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        if (helper.resolveRole(actor) != MemberRole.OWNER) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        ConversationMember target = helper.getMemberOrThrow(conversation, targetUserId);
        if (helper.resolveRole(target) != MemberRole.ADMIN) throw new AppException(ErrorCode.CHAT_TARGET_NOT_ADMIN);

        target.setRole(MemberRole.MEMBER);
        Conversation saved = conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        ActorInfo targetInfo = fetchActorInfo(targetUserId);

        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.DEMOTE_ADMIN,
                Map.of("targetIds", List.of(targetUserId), "payload", Map.of("targetName", targetInfo.name())));

        return broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public ConversationResponse updateGroupName(String conversationId, String name) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
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

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.UPDATE_NAME,
                Map.of("payload", Map.of("oldName", displayOldName, "newName", displayNewName)));

        return broadcastAndRespond(conversation, currentUserId);
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
        Conversation conversation = findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);
        helper.assertSettingAllowed(conversation, currentUserId, GroupSettings::isMemberCanChangeInfo);

        if (file != null && !file.isEmpty()) {
            String folder = "conversations/groups/" + conversationId + "/avatar";
            ApiResponse<FileUploadResponse> uploadResponse = fileServiceClient.upload(file, folder);
            if (uploadResponse != null && uploadResponse.data() != null) {
                String oldAvatarKey = conversation.getAvatar();
                String avatarKey = uploadResponse.data().key();

                conversation.setAvatar(avatarKey);

                ActorInfo actorInfo = fetchActorInfo(currentUserId);
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

                return broadcastAndRespond(conversation, currentUserId);
            }
        }

        helper.broadcastConversationUpdate(conversation);
        return helper.buildConversationResponseForCurrentUser(conversation, currentUserId);
    }

    @Override
    public ConversationResponse updateGroupSettings(String conversationId, UpdateGroupSettingsRequest request) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);

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

        ActorInfo actorInfo = fetchActorInfo(currentUserId);

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

        return broadcastAndRespond(saved, currentUserId);
    }

    @Override
    public void disbandGroup(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);

        boolean isOwner = conversation.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(currentUserId)
                        && helper.isActiveMember(m) && m.getRole() == MemberRole.OWNER);
        if (!isOwner) throw new AppException(ErrorCode.CHAT_NOT_OWNER);

        conversation.setDisbanded(true);
        conversationRepository.save(conversation);
        messageRepository.deleteByConversationId(conversationId);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.DISBAND_GROUP, Map.of());

        helper.broadcastConversationUpdate(conversationId);
        log.info("[Group] Group {} has been disbanded by owner {}", conversationId, currentUserId);
    }

    @Override
    public void leaveGroup(String conversationId, boolean silent, String transferTo) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
        helper.assertMember(conversation, currentUserId);

        ConversationMember currentMember = helper.getMemberOrThrow(conversation, currentUserId);

        ActorInfo transferActorInfo = null;
        ActorInfo transferTargetInfo = null;

        if (helper.resolveRole(currentMember) == MemberRole.OWNER) {
            if (transferTo == null || transferTo.isBlank()) {
                throw new AppException(ErrorCode.CHAT_CANNOT_REMOVE_OWNER);
            }
            ConversationMember target = helper.getMemberOrThrow(conversation, transferTo);
            if (!helper.isActiveMember(target)) throw new AppException(ErrorCode.CHAT_TARGET_NOT_MEMBER);
            if (target.getUserId().equals(currentUserId)) throw new AppException(ErrorCode.CHAT_CANNOT_TRANSFER_TO_SELF);

            currentMember.setRole(MemberRole.MEMBER);
            target.setRole(MemberRole.OWNER);
            transferActorInfo = fetchActorInfo(currentUserId);
            transferTargetInfo = fetchActorInfo(transferTo);
        }

        ActorInfo actorInfo = fetchActorInfo(currentUserId);

        currentMember.setActive(false);
        currentMember.setRemovedAt(LocalDateTime.now());
        if (conversation.getUnreadCounts() != null) conversation.getUnreadCounts().remove(currentUserId);
        conversationRepository.save(conversation);

        if (!silent) {
            systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                    SystemActionType.LEAVE_GROUP, Map.of());
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

        if (transferActorInfo != null) {
            systemMessageService.sendSystemMessage(conversationId, currentUserId, transferActorInfo.name(), transferActorInfo.avatar(),
                    SystemActionType.TRANSFER_OWNER,
                    Map.of("targetIds", List.of(transferTo), "payload", Map.of("targetName", transferTargetInfo.name())));
        }

        helper.broadcastConversationUpdate(conversationId);
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

        Page<ChatUser> candidatesPage;
        if (helper.isPhoneNumber(query)) {
            Optional<ChatUser> userOpt = chatUserRepository.findByPhoneNumber(query.trim())
                    .filter(u -> !u.getId().equals(currentUserId));
            List<ChatUser> list = userOpt.map(Collections::singletonList).orElse(Collections.emptyList());
            candidatesPage = new PageImpl<>(list, pageable, list.size());
        } else {
            ChatUser currentUser = chatUserRepository.findById(currentUserId).orElse(null);
            if (currentUser != null && !currentUser.getFriendIds().isEmpty()) {
                Set<String> friendIds = new HashSet<>(currentUser.getFriendIds());
                friendIds.remove(currentUserId);
                candidatesPage = chatUserRepository.findByIdInAndFullNameContainingIgnoreCase(friendIds, query.trim(), pageable);
            } else {
                candidatesPage = Page.empty(pageable);
            }
        }

        String baseUrl = helper.getBaseUrl();
        return PageResponse.fromPage(candidatesPage, u -> SearchMemberResponse.builder()
                .userId(u.getId()).fullName(u.getFullName())
                .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                .phoneNumber(helper.isPhoneNumber(query) ? u.getPhoneNumber() : null)
                .isAlreadyMember(memberIds.contains(u.getId())).build());
    }

    @Override
    public PageResponse<List<GroupMemberListItemResponse>> getGroupMembers(String conversationId, String query, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
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
                .append("role", "$members.role").append("joinedAt", "$members.joinedAt");

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

        List<GroupMemberListItemResponse> pageData = dataDocs.stream().map(doc -> {
            String userId = doc.getString("userId");
            String fullName = doc.getString("fullName") != null ? doc.getString("fullName") : "Người dùng";
            String avatar = doc.getString("avatar");
            String phoneNumber = doc.getString("phoneNumber");
            String roleRaw = doc.getString("role");
            MemberRole role = roleRaw != null ? MemberRole.valueOf(roleRaw) : MemberRole.MEMBER;
            boolean isCurrentUser = currentUserId.equals(userId);
            boolean isFriend = friendIds.contains(userId);

            return GroupMemberListItemResponse.builder()
                    .userId(userId).fullName(fullName)
                    .avatar(avatar != null ? baseUrl + avatar : null)
                    .phoneNumber(phoneNumber).role(role)
                    .joinedAt(helper.toOffsetFromMongo(doc.get("joinedAt")))
                    .isCurrentUser(isCurrentUser).isFriend(isFriend).build();
        }).toList();

        return PageResponse.<List<GroupMemberListItemResponse>>builder()
                .data(pageData).page(pageable.getPageNumber())
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageable.getPageSize()))
                .limit(pageable.getPageSize()).totalItems(totalItems).build();
    }

    @Override
    public String generateJoinLink(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        if (conversation.getJoinLinkToken() != null) {
            return conversation.getJoinLinkToken();
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        conversation.setJoinLinkToken(token);

        GroupSettings settings = conversation.getSettings();
        if (settings == null) {
            settings = GroupSettings.builder().build();
            conversation.setSettings(settings);
        }
        settings.setJoinByLinkEnabled(true);

        conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.GENERATE_JOIN_LINK, Map.of("payload", Map.of("token", token)));

        helper.broadcastConversationUpdate(conversation);
        log.info("[Group] Join link generated for conversation {} by user {}", conversationId, currentUserId);
        return token;
    }

    @Override
    public String refreshJoinLink(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        String newToken = UUID.randomUUID().toString().replace("-", "");
        conversation.setJoinLinkToken(newToken);

        GroupSettings settings = conversation.getSettings();
        if (settings == null) {
            settings = GroupSettings.builder().build();
            conversation.setSettings(settings);
        }
        settings.setJoinByLinkEnabled(true);

        conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.REFRESH_JOIN_LINK, Map.of("payload", Map.of("token", newToken)));

        helper.broadcastConversationUpdate(conversation);
        log.info("[Group] Join link refreshed for conversation {} by user {}", conversationId, currentUserId);
        return newToken;
    }

    @Override
    public ConversationResponse joinByLink(String token) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();

        Conversation conversation = conversationRepository.findByJoinLinkToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_JOIN_LINK_INVALID));

        if (!conversation.isGroup()) throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);

        GroupSettings settings = conversation.getSettings();
        if (settings == null || !settings.isJoinByLinkEnabled()) {
            throw new AppException(ErrorCode.CHAT_JOIN_LINK_DISABLED);
        }

        boolean isAlreadyActive = conversation.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(currentUserId) && helper.isActiveMember(m));
        if (isAlreadyActive) {
            throw new AppException(ErrorCode.CHAT_ALREADY_MEMBER);
        }

        if (settings.isMembershipApprovalEnabled()) {
            return handleJoinRequest(conversation, currentUserId);
        }

        return directJoinByLink(conversation, currentUserId);
    }

    @Override
    public JoinGroupPreviewResponse getJoinPreview(String token) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();

        Conversation conversation = conversationRepository.findByJoinLinkToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_JOIN_LINK_INVALID));

        if (!conversation.isGroup()) throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);

        GroupSettings settings = conversation.getSettings();
        if (settings == null || !settings.isJoinByLinkEnabled()) {
            throw new AppException(ErrorCode.CHAT_JOIN_LINK_DISABLED);
        }

        Set<ConversationMember> activeMembers = conversation.getMembers().stream()
                .filter(helper::isActiveMember).collect(Collectors.toSet());

        boolean isAlreadyMember = activeMembers.stream()
                .anyMatch(m -> m.getUserId().equals(currentUserId));

        // Single batch query for all member info
        Set<String> allMemberIds = activeMembers.stream()
                .map(ConversationMember::getUserId).collect(Collectors.toSet());
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allMemberIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String ownerUserId = activeMembers.stream()
                .filter(m -> m.getRole() == MemberRole.OWNER)
                .map(ConversationMember::getUserId)
                .findFirst().orElse(null);
        String createdByName = ownerUserId != null && userCache.containsKey(ownerUserId)
                ? userCache.get(ownerUserId).getFullName() : null;

        String baseUrl = helper.getBaseUrl();
        List<JoinGroupPreviewResponse.MemberPreview> memberPreviews = activeMembers.stream()
                .map(ConversationMember::getUserId)
                .limit(5)
                .map(userCache::get)
                .filter(Objects::nonNull)
                .map(u -> JoinGroupPreviewResponse.MemberPreview.builder()
                        .name(u.getFullName())
                        .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                        .build())
                .collect(Collectors.toList());

        String groupName = conversation.getName();
        if (groupName == null || groupName.isBlank()) {
            groupName = helper.getDynamicGroupName(conversation, currentUserId, userCache);
        }

        boolean hasPendingRequest = joinRequestRepository.existsByConversationIdAndUserIdAndStatus(
                conversation.getId(), currentUserId, JoinRequestStatus.PENDING);

        return JoinGroupPreviewResponse.builder()
                .conversationId(conversation.getId())
                .groupName(groupName)
                .groupAvatar(conversation.getAvatar() != null ? baseUrl + conversation.getAvatar() : null)
                .memberCount(activeMembers.size())
                .createdByName(createdByName)
                .memberPreviews(memberPreviews)
                .isAlreadyMember(isAlreadyMember)
                .membershipApprovalEnabled(settings.isMembershipApprovalEnabled())
                .hasPendingRequest(hasPendingRequest)
                .build();
    }

    @Override
    public PageResponse<List<JoinRequestResponse>> getJoinRequests(String conversationId, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        Page<JoinRequest> requestPage = joinRequestRepository.findByConversationIdAndStatus(
                conversationId, JoinRequestStatus.PENDING,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));

        Set<String> userIds = requestPage.getContent().stream()
                .map(JoinRequest::getUserId).collect(Collectors.toSet());
        String baseUrl = helper.getBaseUrl();
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        List<JoinRequestResponse> responses = requestPage.getContent().stream()
                .map(req -> {
                    ChatUser user = userCache.get(req.getUserId());
                    return JoinRequestResponse.builder()
                            .id(req.getId())
                            .conversationId(req.getConversationId())
                            .userId(req.getUserId())
                            .fullName(user != null ? user.getFullName() : "Người dùng")
                            .avatar(user != null && user.getAvatar() != null ? baseUrl + user.getAvatar() : null)
                            .status(req.getStatus())
                            .requestedAt(req.getCreatedAt())
                            .processedAt(req.getProcessedAt())
                            .processedBy(req.getProcessedBy())
                            .build();
                })
                .collect(Collectors.toList());

        return PageResponse.<List<JoinRequestResponse>>builder()
                .page(page)
                .limit(size)
                .totalItems(requestPage.getTotalElements())
                .totalPages(requestPage.getTotalPages())
                .data(responses)
                .build();

    }

    @Override
    public ConversationResponse approveJoinRequest(String conversationId, String requestId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_JOIN_REQUEST_NOT_FOUND));

        if (!joinRequest.getConversationId().equals(conversationId)) {
            throw new AppException(ErrorCode.CHAT_JOIN_REQUEST_NOT_FOUND);
        }
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new AppException(ErrorCode.CHAT_JOIN_REQUEST_ALREADY_PROCESSED);
        }

        joinRequest.setStatus(JoinRequestStatus.APPROVED);
        joinRequest.setProcessedAt(LocalDateTime.now());
        joinRequest.setProcessedBy(currentUserId);
        joinRequestRepository.save(joinRequest);

        String targetUserId = joinRequest.getUserId();
        ConversationResponse response = addMemberToConversation(conversation, targetUserId);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        ActorInfo targetInfo = fetchActorInfo(targetUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId,
                actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_REQUEST_APPROVED,
                Map.of("targetIds", List.of(targetUserId),
                        "payload", Map.of("targetNames", List.of(targetInfo.name()),
                                "targetAvatars", List.of(targetInfo.avatar() != null ? targetInfo.avatar() : ""))));

        log.info("[Group] Join request {} approved by {} for user {} in conversation {}",
                requestId, currentUserId, targetUserId, conversationId);
        return response;
    }

    @Override
    public void rejectJoinRequest(String conversationId, String requestId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_JOIN_REQUEST_NOT_FOUND));

        if (!joinRequest.getConversationId().equals(conversationId)) {
            throw new AppException(ErrorCode.CHAT_JOIN_REQUEST_NOT_FOUND);
        }
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new AppException(ErrorCode.CHAT_JOIN_REQUEST_ALREADY_PROCESSED);
        }

        joinRequest.setStatus(JoinRequestStatus.REJECTED);
        joinRequest.setProcessedAt(LocalDateTime.now());
        joinRequest.setProcessedBy(currentUserId);
        joinRequestRepository.save(joinRequest);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId,
                actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_REQUEST_REJECTED, Map.of(),
                Set.of(joinRequest.getUserId()));

        log.info("[Group] Join request {} rejected by {} for user {} in conversation {}",
                requestId, currentUserId, joinRequest.getUserId(), conversationId);
    }

    @Override
    public void cancelMyJoinRequest(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();

        JoinRequest joinRequest = joinRequestRepository
                .findByConversationIdAndUserIdAndStatus(conversationId, currentUserId, JoinRequestStatus.PENDING)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_JOIN_REQUEST_NOT_FOUND));

        joinRequest.setStatus(JoinRequestStatus.CANCELLED);
        joinRequest.setProcessedAt(LocalDateTime.now());
        joinRequest.setProcessedBy(currentUserId);
        joinRequestRepository.save(joinRequest);

        log.info("[Group] User {} cancelled join request for conversation {}", currentUserId, conversationId);
    }

    private ConversationResponse handleJoinRequest(Conversation conversation, String currentUserId) {
        boolean alreadyPending = joinRequestRepository.existsByConversationIdAndUserIdAndStatus(
                conversation.getId(), currentUserId, JoinRequestStatus.PENDING);
        if (alreadyPending) {
            throw new AppException(ErrorCode.CHAT_JOIN_REQUEST_ALREADY_PENDING);
        }

        JoinRequest joinRequest = JoinRequest.builder()
                .conversationId(conversation.getId())
                .userId(currentUserId)
                .status(JoinRequestStatus.PENDING)
                .build();
        joinRequestRepository.save(joinRequest);

        Set<String> adminIds = conversation.getMembers().stream()
                .filter(m -> helper.isActiveMember(m) && helper.resolveRole(m) != MemberRole.MEMBER)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversation.getId(), currentUserId,
                actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_REQUEST_CREATED, Map.of(),
                adminIds);

        log.info("[Group] Join request created by user {} for conversation {}", currentUserId, conversation.getId());

        return null;
    }

    private ConversationResponse directJoinByLink(Conversation conversation, String currentUserId) {
        LocalDateTime now = LocalDateTime.now();
        ConversationMember existingMember = conversation.getMembers().stream()
                .filter(m -> m.getUserId().equals(currentUserId))
                .findFirst().orElse(null);

        if (existingMember != null) {
            existingMember.setActive(true);
            existingMember.setRemovedAt(null);
            existingMember.setRemovedBy(null);
            existingMember.setRole(MemberRole.MEMBER);
            existingMember.setJoinedAt(now);
        } else {
            conversation.getMembers().add(
                    ConversationMember.builder().userId(currentUserId).role(MemberRole.MEMBER).joinedAt(now).build());
        }

        if (conversation.getUnreadCounts() == null) conversation.setUnreadCounts(new HashMap<>());
        conversation.getUnreadCounts().putIfAbsent(currentUserId, 0);

        Conversation saved = conversationRepository.save(conversation);

        ActorInfo actorInfo = fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(saved.getId(), currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_BY_LINK, Map.of());

        log.info("[Group] User {} joined conversation {} via link", currentUserId, saved.getId());
        return broadcastAndRespond(saved, currentUserId);
    }

    private ConversationResponse addMemberToConversation(Conversation conversation, String userId) {
        LocalDateTime now = LocalDateTime.now();
        ConversationMember existingMember = conversation.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst().orElse(null);

        if (existingMember != null) {
            existingMember.setActive(true);
            existingMember.setRemovedAt(null);
            existingMember.setRemovedBy(null);
            existingMember.setRole(MemberRole.MEMBER);
            existingMember.setJoinedAt(now);
        } else {
            conversation.getMembers().add(
                    ConversationMember.builder().userId(userId).role(MemberRole.MEMBER).joinedAt(now).build());
        }

        if (conversation.getUnreadCounts() == null) conversation.setUnreadCounts(new HashMap<>());
        conversation.getUnreadCounts().putIfAbsent(userId, 0);

        Conversation saved = conversationRepository.save(conversation);
        return broadcastAndRespond(saved, userId);
    }

    private Conversation findGroupConversation(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!conversation.isGroup()) throw new AppException(ErrorCode.CHAT_NOT_A_GROUP);
        return conversation;
    }

    private ActorInfo fetchActorInfo(String userId) {
        return ActorInfo.of(chatUserRepository.findById(userId).orElse(null), "Người dùng");
    }

    private ConversationResponse broadcastAndRespond(Conversation saved, String currentUserId) {
        helper.broadcastConversationUpdate(saved.getId());
        return helper.buildConversationResponseForCurrentUser(saved, currentUserId);
    }

    private Set<String> getActiveMemberIds(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return new HashSet<>();
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .getMembers().stream().filter(helper::isActiveMember)
                .map(ConversationMember::getUserId).collect(Collectors.toSet());
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
