package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.messageservice.dto.request.GroupInviteSendRequest;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.service.message.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupInviteServiceImpl implements GroupInviteService {

    private final ConversationHelper helper;
    private final ConversationService conversationService;
    private final MessageService messageService;

    @Value("${bondhub.frontend-url}")
    private String frontendUrl;

    @Override
    public void sendInvites(String conversationId, GroupInviteSendRequest request) {
        String currentUserId = helper.getSecurityUtil().getCurrentUserId();

        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);

        helper.assertMember(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        if (conversation.getJoinLinkToken() == null || conversation.getJoinLinkToken().isBlank()) {
            throw new AppException(ErrorCode.CHAT_JOIN_LINK_DISABLED);
        }

        Set<String> requestedUserIds = new LinkedHashSet<>(
                request != null && request.userIds() != null ? request.userIds() : Set.of());
        requestedUserIds.remove(currentUserId);

        if (requestedUserIds.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Set<String> invitedUserIds = conversation.getInvitedUserIds() != null
                ? conversation.getInvitedUserIds()
                : Set.of();

        Set<String> invalidUserIds = requestedUserIds.stream()
                .filter(userId -> !invitedUserIds.contains(userId))
                .collect(Collectors.toSet());

        if (!invalidUserIds.isEmpty()) {
            throw new AppException(ErrorCode.CHAT_INVALID_INVITE_TARGET);
        }

        String joinLinkUrl = frontendUrl + "/g/" + conversation.getJoinLinkToken();

        for (String targetUserId : requestedUserIds) {
            try {
                Conversation directConversation = conversationService.getOrCreateDirectConversation(currentUserId,
                        targetUserId);

                messageService.sendMessage(
                        directConversation.getId(),
                        new MessageSendRequest(
                                directConversation.getId(),
                                null,
                                joinLinkUrl,
                                UUID.randomUUID().toString(),
                                null,
                                false,
                                null));
            } catch (Exception e) {
                log.warn("[Group] Failed to send invite message to user {} for group {}: {}",
                        targetUserId, conversationId, e.getMessage());
            }
        }

        log.info("[Group] User {} sent {} invite(s) for conversation {}",
                currentUserId, requestedUserIds.size(), conversationId);
    }
}
