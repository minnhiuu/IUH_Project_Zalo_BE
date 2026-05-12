package com.bondhub.messageservice.service.message;

import com.bondhub.messageservice.dto.response.MessageSyncResponse;

import java.util.List;

public interface MessageInternalService {
    List<MessageSyncResponse> getMessagesBatch(String lastId, int batchSize);
    long getMessageCount();
}
