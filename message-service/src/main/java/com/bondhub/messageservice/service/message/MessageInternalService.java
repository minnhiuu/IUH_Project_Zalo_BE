package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.client.messageservice.RecentChatInteractionRequest;
import com.bondhub.common.dto.client.messageservice.RecentChatInteractionResponse;
import com.bondhub.messageservice.dto.response.MessageSyncResponse;

import java.util.List;

public interface MessageInternalService {
    List<MessageSyncResponse> getMessagesBatch(String lastId, int batchSize);
    long getMessageCount();
    List<RecentChatInteractionResponse> getRecentChatInteractions(RecentChatInteractionRequest request);
}
