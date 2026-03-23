package com.leafy.socialfeedservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.leafy.socialfeedservice.dto.request.user.UserInterestSeedUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/internal/users/account/{accountId}/summary")
    ApiResponse<UserSummaryResponse> getUserSummaryByAccountId(
            @PathVariable("accountId") String accountId
    );

    @PutMapping("/internal/users/account/{accountId}/seed-interests")
    ApiResponse<Void> updateUserInterestsForSeed(
            @PathVariable("accountId") String accountId,
            @RequestBody UserInterestSeedUpdateRequest request
    );
}