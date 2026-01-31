package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.UserProfileResponse;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByAccountId(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByAccountId(accountId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUser(
            @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyUserWithAccountInfo()));
    }

    @PatchMapping("/profile/avatar")
    public ResponseEntity<ApiResponse<String>> updateAvatar(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateAvatar(file)));
    }

    @PatchMapping("/profile/background")
    public ResponseEntity<ApiResponse<String>> updateBackground(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateBackground(file)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

}
