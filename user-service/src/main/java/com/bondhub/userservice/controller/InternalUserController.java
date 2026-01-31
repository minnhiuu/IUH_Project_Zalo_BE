package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {
    private final UserService userService;

    @GetMapping("/account/{accountId}/summary")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUserSummaryByAccountId(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserSummaryByAccountId(accountId)));
    }

}
