package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ApiResponse<UserResponse> createUser(@RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable String id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @GetMapping("/account/{accountId}")
    public ApiResponse<UserResponse> getUserByAccountId(@PathVariable String accountId) {
        return ApiResponse.success(userService.getUserByAccountId(accountId));
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> getAllUsers() {
        return ApiResponse.success(userService.getAllUsers());
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateUser(id, request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyProfile() {
        return ApiResponse.success(userService.getMyUserWithAccountInfo());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ApiResponse.successWithoutData();
    }

}
