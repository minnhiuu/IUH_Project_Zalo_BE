package com.bondhub.notificationservices.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.notificationservices.dto.response.notification.NotificationAcceptedResponse;
import com.bondhub.notificationservices.dto.response.notification.NotificationFlatHistoryResponse;
import com.bondhub.notificationservices.dto.response.notification.NotificationHistoryResponse;
import com.bondhub.notificationservices.dto.response.notification.UserNotificationStateResponse;
import com.bondhub.notificationservices.service.notification.NotificationService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {
    
    NotificationService notificationService;

    @PostMapping("/test-fcm")
    public ResponseEntity<ApiResponse<Void>> testFcm() {
        notificationService.sendTestNotification();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<NotificationHistoryResponse>> getNotificationHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime cursor,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotificationHistory(cursor, limit)));
    }

    @GetMapping("/history/unread")
    public ResponseEntity<ApiResponse<NotificationFlatHistoryResponse>> getUnreadNotificationHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime cursor,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadHistory(cursor, limit)));
    }

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<UserNotificationStateResponse>> getNotificationState() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotificationState()));
    }

    @PostMapping("/checked")
    public ResponseEntity<ApiResponse<Void>> markHistoryAsChecked() {
        notificationService.markHistoryAsChecked();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
