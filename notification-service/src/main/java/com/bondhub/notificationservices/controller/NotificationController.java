package com.bondhub.notificationservices.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.notificationservices.dto.request.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.NotificationResponse;
import com.bondhub.notificationservices.service.notification.NotificationService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class NotificationController {

    NotificationService notificationService;

    @PostMapping("/friend-request")
    public ResponseEntity<ApiResponse<NotificationResponse>> createFriendRequest(@Valid @RequestBody CreateFriendRequestNotificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.createFriendRequestNotification(request)));
    }
}
