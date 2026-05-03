package com.bondhub.searchservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "message-service")
public interface ConversationMemberClient {

    @GetMapping("/internal/conversations/{conversationId}/members/{userId}")
    ApiResponse<ConversationMemberLookupResponse> getConversationMember(
            @PathVariable("conversationId") String conversationId,
            @PathVariable("userId") String userId);

    @GetMapping("/internal/conversations/search")
    ApiResponse<PageResponse<List<ConversationSearchResponse>>> searchConversations(
            @RequestParam("userId") String userId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "isGroup", required = false) Boolean isGroup,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
