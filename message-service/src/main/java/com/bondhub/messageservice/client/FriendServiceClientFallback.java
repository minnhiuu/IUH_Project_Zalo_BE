package com.bondhub.messageservice.client;

import com.bondhub.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
@Slf4j
public class FriendServiceClientFallback implements FriendServiceClient {

    @Override
    public ApiResponse<Set<String>> getFriendIds(String userId) {
        log.warn("Fallback triggered: Unable to fetch friend IDs for user {}", userId);
        return ApiResponse.success(Collections.emptySet());
    }
}
