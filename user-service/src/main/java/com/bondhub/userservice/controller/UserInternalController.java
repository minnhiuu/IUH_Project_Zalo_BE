package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.response.UserSyncResponse;
import com.bondhub.userservice.service.user.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserInternalService userInternalService;

    @GetMapping("/account/{accountId}/summary")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUserSummaryByAccountId(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success(userInternalService.getUserSummaryByAccountId(accountId)));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> getUserCount() {
        return ResponseEntity.ok(ApiResponse.success(userInternalService.getUserCount()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserSyncResponse>> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(userInternalService.getUserById(userId)));
    }

    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<List<UserSyncResponse>>> getUsersBatch(
            @RequestParam(required = false) String lastId,
            @RequestParam(defaultValue = "500") int size) {
        return ResponseEntity.ok(ApiResponse.success(userInternalService.getUsersBatch(lastId, size)));
    }

    @PostMapping("/{accountId}/last-login")
    public ResponseEntity<ApiResponse<Void>> recordLastLogin(@PathVariable String accountId) {
        userInternalService.recordLastLogin(accountId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/account/{accountId}/ban-status")
    public ResponseEntity<ApiResponse<Void>> syncBanStatus(
            @PathVariable String accountId,
            @RequestParam boolean banned) {
        userInternalService.syncBanStatus(accountId, banned);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
