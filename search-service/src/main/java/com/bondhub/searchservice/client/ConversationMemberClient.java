package com.bondhub.searchservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "message-service")
public interface ConversationMemberClient {

    @GetMapping("/internal/conversations/{conversationId}/members/{userId}")
    ApiResponse<ConversationMemberLookupResponse> getConversationMember(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("userId") String userId);
}
