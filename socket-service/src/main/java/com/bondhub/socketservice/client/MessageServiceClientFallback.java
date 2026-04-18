package com.bondhub.socketservice.client;

import com.bondhub.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
@Slf4j
public class MessageServiceClientFallback implements MessageServiceClient {

    @Override
    public ApiResponse<Set<String>> getConversationMemberIds(String conversationId) {
        log.warn("[Feign] Fallback: getConversationMemberIds for {}", conversationId);
        return ApiResponse.success(Collections.emptySet());
    }
}
