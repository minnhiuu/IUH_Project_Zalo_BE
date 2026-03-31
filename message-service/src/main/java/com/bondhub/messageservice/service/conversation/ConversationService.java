package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.model.Conversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationService {
    Optional<Conversation> getDirectConversation(
            String senderId,
            String recipientId,
            boolean createNewRoomIfNotExists);

    Conversation createInitialChatRoom(String userA, String userB, LocalDateTime timestamp);

    ConversationResponse getConversationForUser(String userId, String partnerId);

    PageResponse<List<ConversationResponse>> getUserConversations(int page, int size);

    void markAsRead(String conversationId);
}
