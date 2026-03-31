package com.bondhub.socketservice.client;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FriendServiceClientFallback implements FriendServiceClient {

    @Override
    public ApiResponse<Set<String>> getFriendIds(String userId) {
        return ApiResponse.success(Set.of());
    }
}
