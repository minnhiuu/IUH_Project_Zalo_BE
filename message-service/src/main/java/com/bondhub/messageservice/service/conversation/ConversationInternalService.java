package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;

import java.util.List;

public interface ConversationInternalService {
    ConversationMemberLookupResponse getConversationMember(String conversationId, String userId);

    PageResponse<List<ConversationSearchResponse>> searchConversations(
            String userId,
            String keyword,
            Boolean isGroup,
            int page,
            int size);
}
