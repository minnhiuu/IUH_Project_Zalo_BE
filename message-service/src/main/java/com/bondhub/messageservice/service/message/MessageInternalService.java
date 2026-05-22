package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.client.messageservice.ChatInteractionFeatureSnapshotResponse;
import com.bondhub.messageservice.dto.response.MessageSyncResponse;

import java.util.List;

public interface MessageInternalService {
    List<MessageSyncResponse> getMessagesBatch(String lastId, int batchSize);
    long getMessageCount();
    List<ChatInteractionFeatureSnapshotResponse> getSearchInteractionFeatureSnapshot(int sinceDays, int limit);
}
