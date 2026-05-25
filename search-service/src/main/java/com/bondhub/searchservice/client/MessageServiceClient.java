package com.bondhub.searchservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.messageservice.ChatInteractionFeatureSnapshotResponse;
import com.bondhub.searchservice.dto.response.MessageSyncResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "message-service")
public interface MessageServiceClient {

    @GetMapping("/internal/messages/sync/batch")
    ApiResponse<List<MessageSyncResponse>> getMessagesBatch(
            @RequestParam(value = "lastId", required = false) String lastId,
            @RequestParam("batchSize") int batchSize);

    @GetMapping("/internal/messages/sync/count")
    ApiResponse<Long> getMessageCount();

    @GetMapping("/internal/messages/search-interaction-features/snapshot")
    ApiResponse<List<ChatInteractionFeatureSnapshotResponse>> getSearchInteractionFeatureSnapshot(
            @RequestParam("sinceDays") int sinceDays,
            @RequestParam("limit") int limit);
}
