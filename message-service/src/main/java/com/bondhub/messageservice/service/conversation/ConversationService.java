package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.messageservice.dto.response.ConversationParticipantResponse;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.dto.response.UnreadAnchorResponse;
import com.bondhub.messageservice.model.Conversation;

import java.util.List;
import java.util.Set;

public interface ConversationService {

    Conversation getOrCreateDirectConversation(String userA, String userB);

    ConversationResponse getOrCreateConversationForUser(String partnerId);

    PageResponse<List<ConversationResponse>> getUserConversations(int page, int size);
    
    List<UserSummaryResponse> getQuickConversations(int size);

    void markAsRead(String conversationId, String lastReadMessageId);

    UnreadAnchorResponse getUnreadAnchor(String conversationId);

    void clearChatHistory(String conversationId);

    void deleteConversationForMe(String conversationId);

    Set<String> getConversationMemberIds(String conversationId);

    PageResponse<List<ConversationParticipantResponse>> getConversationParticipants(
            String conversationId, String query, int page, int size);

    void markAsUnread(String conversationId);

    void togglePin(String conversationId, boolean pin);

    void toggleMute(String conversationId, boolean mute);

    void toggleHide(String conversationId, boolean hide);
}
