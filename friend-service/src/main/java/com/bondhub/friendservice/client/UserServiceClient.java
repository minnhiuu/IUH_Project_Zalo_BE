package com.bondhub.friendservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", path = "/users")
public interface UserServiceClient {
    
    @GetMapping("/{id}")
    ApiResponse<UserSummaryResponse> getUserSummary(@PathVariable("id") String id);
    
    @GetMapping("/account/{accountId}")
    ApiResponse<UserSummaryResponse> getUserByAccountId(@PathVariable("accountId") String accountId);
    
    @GetMapping("/batch")
    ApiResponse<List<UserSummaryResponse>> getUsersByIds(@RequestParam("ids") List<String> ids);
}
