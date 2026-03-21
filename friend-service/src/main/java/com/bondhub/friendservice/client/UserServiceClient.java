package com.bondhub.friendservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-service", path = "/users")
public interface UserServiceClient {

    @GetMapping("/{id}")
    ApiResponse<UserSummaryResponse> getUserSummary(@PathVariable("id") String id);
    
    @PostMapping("/batch")
    ApiResponse<Map<String, UserSummaryResponse>> getUsersByIds(@RequestBody List<String> ids);

    @GetMapping("/account/{accountId}")
    ApiResponse<UserSummaryResponse> getUserByAccountId(@PathVariable("accountId") String accountId);

//    @GetMapping("/batch")
//    ApiResponse<List<UserSummaryResponse>> getUsersByIds(@PathVariable("ids") List<String> ids);
}
