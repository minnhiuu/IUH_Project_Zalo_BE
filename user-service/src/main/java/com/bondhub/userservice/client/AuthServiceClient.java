package com.bondhub.userservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.dto.response.AccountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/auth/accounts/{id}")
    ApiResponse<AccountResponse> getAccountById(@PathVariable("id") String id);

    @PostMapping("/auth/accounts/batch")
    ApiResponse<List<AccountResponse>> getAccountsByIds(@RequestBody List<String> ids);
}
