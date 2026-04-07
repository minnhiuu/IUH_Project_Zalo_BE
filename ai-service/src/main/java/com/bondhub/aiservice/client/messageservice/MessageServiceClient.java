package com.bondhub.aiservice.client.messageservice;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "message-service", fallback = MessageServiceClientFallback.class)
public interface MessageServiceClient {

    /** Lấy danh sách phòng chat của currentUser */
    @GetMapping("/messages/conversations")
    ApiResponse<Map<String, Object>> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    /** Lấy tin nhắn theo conversationId */
    @GetMapping("/messages/conversations/{conversationId}/messages")
    ApiResponse<Map<String, Object>> getMessages(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size);
}
