package com.bondhub.authservice.client;

import com.bondhub.common.dto.client.userservice.user.request.UserCreateRequest;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    /**
     * Test public endpoint from UserService
     * This endpoint does not require authentication
     *
     * @return Response containing message and timestamp
     */
    @GetMapping("/users/test/security/public")
    ResponseEntity<ApiResponse<Map<String, Object>>> testPublicEndpoint();

    @PostMapping("/users")
    ApiResponse<UserResponse> createUser(@RequestBody UserCreateRequest request);

    @GetMapping("/internal/users/account/{accountId}/summary")
    ApiResponse<UserSummaryResponse> getUserSummaryByAccountId(@PathVariable String accountId);
}
