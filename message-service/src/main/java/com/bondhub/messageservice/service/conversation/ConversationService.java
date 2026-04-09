package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.model.Conversation;

import java.util.List;

public interface ConversationService {

    Conversation getOrCreateDirectConversation(String userA, String userB);

    ConversationResponse getOrCreateConversationForUser(String partnerId);

    PageResponse<List<ConversationResponse>> getUserConversations(int page, int size);

    void markAsRead(String conversationId);

    void deleteConversationForMe(String conversationId);
}
