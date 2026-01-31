package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {
    private final UserService userService;

    @GetMapping("/account/{accountId}/summary")
    public ApiResponse<UserSummaryResponse> getUserSummaryByAccountId(@PathVariable String accountId) {
        return ApiResponse.success(userService.getUserSummaryByAccountId(accountId));
    }

}
