package com.leafy.socialfeedservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.leafy.socialfeedservice.dto.response.account.AccountSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Feign client for communicating with the auth-service.
 * Used internally to fetch real account IDs for mock data seeding.
 */
@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    /**
     * Retrieves all accounts from auth-service.
     *
     * @return ApiResponse wrapping a list of {@link AccountSummaryResponse}
     */
    @GetMapping("/auth/accounts")
    ApiResponse<List<AccountSummaryResponse>> getAllAccounts();
}
