package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.client.messageservice.MessageSendRequest;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.service.message.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupInviteAsyncService {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @Value("${bondhub.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendJoinLinkInvites(Conversation groupConversation, String senderUserId, Set<String> targetUserIds) {
        String joinLinkToken = groupConversation.getJoinLinkToken();
        if (joinLinkToken == null)
            return;

        String joinLinkUrl = frontendUrl + "/g/" + joinLinkToken;

        for (String targetUserId : targetUserIds) {
            try {
                Conversation directConv = conversationService.getOrCreateDirectConversation(senderUserId, targetUserId);
                messageService.sendMessage(directConv.getId(),
                        new MessageSendRequest(directConv.getId(), null, joinLinkUrl,
                                UUID.randomUUID().toString(), null, false, null));
            } catch (Exception e) {
                log.warn("[Group] Failed to send join link invite to user {}: {}", targetUserId, e.getMessage());
            }
        }
    }

    @Async
    public void sendJoinLinkInvite(Conversation groupConversation, String senderUserId, String targetUserId) {
        sendJoinLinkInvites(groupConversation, senderUserId, Set.of(targetUserId));
    }
}
