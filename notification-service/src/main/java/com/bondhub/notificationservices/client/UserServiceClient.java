package com.bondhub.notificationservices.client;

import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;
import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/internal/users/exists/{userId}")
    ResponseEntity<ApiResponse<Boolean>> existsById(@PathVariable String userId);

    @GetMapping("/internal/users/id/{userId}/summary")
    ResponseEntity<ApiResponse<UserSummaryResponse>> getUserSummaryByUserId(@PathVariable String userId);

    @GetMapping("/internal/users/settings/notifications/{userId}")
    ResponseEntity<ApiResponse<UserNotificationPreferenceResponse>> getNotificationPreferences(@PathVariable String userId);
}
