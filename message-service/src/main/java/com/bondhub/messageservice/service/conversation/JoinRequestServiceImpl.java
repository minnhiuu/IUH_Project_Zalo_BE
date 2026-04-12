package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.request.JoinByLinkRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.JoinGroupPreviewResponse;
import com.bondhub.messageservice.dto.response.JoinRequestResponse;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.GroupSettings;
import com.bondhub.messageservice.model.JoinRequest;
import com.bondhub.messageservice.model.enums.JoinRequestStatus;
import com.bondhub.messageservice.model.enums.MemberRole;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.JoinRequestRepository;
import com.bondhub.messageservice.service.message.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinRequestServiceImpl implements JoinRequestService {

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final SystemMessageService systemMessageService;
    private final ConversationHelper helper;

    @Override
    public ConversationResponse joinByLink(String token, JoinByLinkRequest request) {
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

        if (settings.isMembershipApprovalEnabled() || settings.getJoinQuestion() != null) {
            String joinAnswer = request != null ? request.joinAnswer() : null;
            return handleJoinRequest(conversation, currentUserId, joinAnswer);
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
                .joinQuestion(settings.getJoinQuestion())
                .build();
    }

    @Override
    public PageResponse<List<JoinRequestResponse>> getJoinRequests(String conversationId, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
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
                            .joinAnswer(req.getJoinAnswer())
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
        Conversation conversation = helper.findGroupConversation(conversationId);
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

        var actorInfo = helper.fetchActorInfo(currentUserId);
        var targetInfo = helper.fetchActorInfo(targetUserId);
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
        Conversation conversation = helper.findGroupConversation(conversationId);
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

        var actorInfo = helper.fetchActorInfo(currentUserId);
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

    @Override
    public void updateJoinQuestion(String conversationId, String question) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        GroupSettings settings = conversation.getSettings();
        if (settings == null) {
            settings = GroupSettings.builder().build();
            conversation.setSettings(settings);
        }
        if (!settings.isMembershipApprovalEnabled()) {
            throw new AppException(ErrorCode.CHAT_APPROVAL_NOT_ENABLED);
        }

        settings.setJoinQuestion(question != null ? question.trim() : null);
        conversationRepository.save(conversation);

        log.info("[Group] Join question updated for conversation {} by user {}", conversationId, currentUserId);
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    private ConversationResponse handleJoinRequest(Conversation conversation, String currentUserId, String joinAnswer) {
        boolean alreadyPending = joinRequestRepository.existsByConversationIdAndUserIdAndStatus(
                conversation.getId(), currentUserId, JoinRequestStatus.PENDING);
        if (alreadyPending) {
            throw new AppException(ErrorCode.CHAT_JOIN_REQUEST_ALREADY_PENDING);
        }

        GroupSettings settings = conversation.getSettings();
        if (settings != null && settings.getJoinQuestion() != null) {
            if (joinAnswer == null || joinAnswer.isBlank()) {
                throw new AppException(ErrorCode.CHAT_JOIN_QUESTION_REQUIRED);
            }
        }

        JoinRequest joinRequest = JoinRequest.builder()
                .conversationId(conversation.getId())
                .userId(currentUserId)
                .status(JoinRequestStatus.PENDING)
                .joinAnswer(joinAnswer)
                .build();
        joinRequestRepository.save(joinRequest);

        Set<String> adminIds = conversation.getMembers().stream()
                .filter(m -> helper.isActiveMember(m) && helper.resolveRole(m) != MemberRole.MEMBER)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        var actorInfo = helper.fetchActorInfo(currentUserId);
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

        boolean canReadRecent = conversation.getSettings() == null || conversation.getSettings().isNewMembersCanReadRecent();
        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        if (canReadRecent) {
            conversation.getDeletedBefore().remove(currentUserId);
        } else {
            conversation.getDeletedBefore().put(currentUserId, now);
        }

        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(saved.getId(), currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_BY_LINK, Map.of());

        log.info("[Group] User {} joined conversation {} via link", currentUserId, saved.getId());
        return helper.broadcastAndRespond(saved, currentUserId);
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

        boolean canReadRecent = conversation.getSettings() == null || conversation.getSettings().isNewMembersCanReadRecent();
        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        if (canReadRecent) {
            conversation.getDeletedBefore().remove(userId);
        } else {
            conversation.getDeletedBefore().put(userId, LocalDateTime.now());
        }

        Conversation saved = conversationRepository.save(conversation);
        return helper.broadcastAndRespond(saved, userId);
    }
}
