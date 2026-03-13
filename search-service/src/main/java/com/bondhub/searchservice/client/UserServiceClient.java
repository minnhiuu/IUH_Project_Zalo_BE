package com.bondhub.searchservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.searchservice.dto.response.UserSyncResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/internal/users/count")
    ApiResponse<Long> getUserCount();

    @GetMapping("/internal/users/{userId}")
    ApiResponse<UserSyncResponse> getUserById(@PathVariable("userId") String userId);

    @GetMapping("/internal/users/batch")
    ApiResponse<List<UserSyncResponse>> getUsersBatch(
            @RequestParam(value = "lastId", required = false) String lastId,
            @RequestParam(value = "size", defaultValue = "500") int size);
}
