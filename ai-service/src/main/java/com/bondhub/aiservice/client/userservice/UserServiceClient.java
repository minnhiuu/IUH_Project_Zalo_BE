package com.bondhub.aiservice.client.userservice;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    /** Lấy thông tin profile của currentUser (dựa vào JWT userId trong header) */
    @GetMapping("/users/me")
    ApiResponse<Map<String, Object>> getMyProfile();
}
