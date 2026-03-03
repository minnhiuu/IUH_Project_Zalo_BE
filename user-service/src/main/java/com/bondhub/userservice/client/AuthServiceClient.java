package com.bondhub.userservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.dto.response.AccountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/auth/accounts/{id}")
    ApiResponse<AccountResponse> getAccountById(@PathVariable("id") String id);

    /** Batch fetch multiple accounts in one call — avoids N+1 */
    @PostMapping("/auth/accounts/internal/batch")
    ApiResponse<List<AccountResponse>> getAccountsByIds(@RequestBody List<String> ids);

    @PostMapping("/auth/accounts/internal/{id}/ban")
    ApiResponse<Void> banAccount(@PathVariable("id") String id, @RequestParam("reason") String reason);

    @PostMapping("/auth/accounts/internal/{id}/unban")
    ApiResponse<Void> unbanAccount(@PathVariable("id") String id);
}
