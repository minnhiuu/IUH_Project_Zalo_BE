package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;

public interface ConversationInternalService {
    ConversationMemberLookupResponse getConversationMember(String conversationId, String userId);

}
