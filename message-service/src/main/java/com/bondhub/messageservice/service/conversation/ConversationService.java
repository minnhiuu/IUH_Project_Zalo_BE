package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.messageservice.dto.request.GroupConversationCreateRequest;
import com.bondhub.messageservice.dto.response.ConversationResponse;
import com.bondhub.messageservice.model.Conversation;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConversationService {

    Conversation getOrCreateDirectConversation(String userA, String userB);

    ConversationResponse getOrCreateConversationForUser(String partnerId);

    PageResponse<List<ConversationResponse>> getUserConversations(int page, int size);

    void markAsRead(String conversationId);

    ConversationResponse createGroupConversation(GroupConversationCreateRequest request);
    
    ConversationResponse updateGroupName(String conversationId, String name);
    
    ConversationResponse updateGroupAvatar(String conversationId, MultipartFile file);
    
    void broadcastConversationUpdate(String conversationId);
    
    void broadcastConversationUpdate(Conversation room);

    void disbandGroup(String conversationId);
}
