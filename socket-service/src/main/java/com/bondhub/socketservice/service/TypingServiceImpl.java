package com.bondhub.socketservice.service;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.socketservice.client.MessageServiceClient;
import com.bondhub.socketservice.dto.TypingPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TypingServiceImpl implements TypingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageServiceClient messageServiceClient;

    // Cache: conversationId → Set<memberId> to avoid calling message-service repeatedly
    private final ConcurrentHashMap<String, Set<String>> memberCache = new ConcurrentHashMap<>();

    @Override
    public void broadcast(TypingPayload payload, String senderId) {
        Set<String> members = getOrFetchMembers(payload.conversationId());
        if (members == null || members.isEmpty()) return;

        for (String memberId : members) {
            if (memberId.equals(senderId)) continue;
            messagingTemplate.convertAndSendToUser(memberId, "/queue/typing", payload);
        }
    }

    private Set<String> getOrFetchMembers(String conversationId) {
        return memberCache.computeIfAbsent(conversationId, id -> {
            try {
                ApiResponse<Set<String>> response = messageServiceClient.getConversationMemberIds(id);
                return response != null && response.data() != null ? response.data() : Set.of();
            } catch (Exception e) {
                log.warn("[Typing] Failed to fetch members for conversation {}: {}", id, e.getMessage());
                return Set.of();
            }
        });
    }

    /**
     * Called externally when membership changes so cache stays fresh.
     */
    public void invalidateMemberCache(String conversationId) {
        memberCache.remove(conversationId);
    }
}
