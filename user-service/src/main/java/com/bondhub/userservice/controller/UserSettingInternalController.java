package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.request.UserSearchVisibilityRequest;
import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSearchVisibilityResponse;
import com.bondhub.userservice.service.settings.UserSettingInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/users/settings")
@RequiredArgsConstructor
public class UserSettingInternalController {

    private final UserSettingInternalService userSettingInternalService;

    @GetMapping("/notifications/{userId}")
    public ResponseEntity<ApiResponse<UserNotificationPreferenceResponse>> getInternalNotificationPreferences(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(userSettingInternalService.getInternalNotificationPreferences(userId)));
    }

    @PostMapping("/search-visibility")
    public ResponseEntity<ApiResponse<List<UserSearchVisibilityResponse>>> getSearchVisibility(
            @RequestBody UserSearchVisibilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userSettingInternalService.getSearchVisibility(request.targetUserIds())));
    }
}
