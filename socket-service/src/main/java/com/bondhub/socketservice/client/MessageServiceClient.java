package com.bondhub.socketservice.client;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Set;

@FeignClient(name = "message-service", fallback = MessageServiceClientFallback.class)
public interface MessageServiceClient {

    @GetMapping("/internal/conversations/{conversationId}/member-ids")
    ApiResponse<Set<String>> getConversationMemberIds(@PathVariable("conversationId") String conversationId);
}
