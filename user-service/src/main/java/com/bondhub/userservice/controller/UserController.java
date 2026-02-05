package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.request.AvatarUpdateRequest;
import com.bondhub.userservice.dto.request.BackgroundUpdateRequest;
import com.bondhub.userservice.dto.response.UserImageResponse;
import com.bondhub.userservice.dto.response.UserProfileResponse;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.service.user.UserService;
import com.bondhub.userservice.service.user.UserSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final UserSearchService userSearchService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {
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
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyUserWithAccountInfo()));
    }

    @PatchMapping("/profile/avatar")
    public ResponseEntity<ApiResponse<UserImageResponse>> updateAvatar(
            @RequestPart("file") MultipartFile file) {
        AvatarUpdateRequest request = AvatarUpdateRequest.builder().file(file).build();
        return ResponseEntity.ok(ApiResponse.success(userService.updateAvatar(request)));
    }

    @PatchMapping("/profile/background")
    public ResponseEntity<ApiResponse<UserImageResponse>> updateBackground(
            @RequestPart("file") MultipartFile file,
            @RequestParam("y") Double y) {
        BackgroundUpdateRequest request = BackgroundUpdateRequest.builder()
                .file(file)
                .y(y)
                .build();
        return ResponseEntity.ok(ApiResponse.success(userService.updateBackground(request)));
    }

    @PatchMapping("/profile/background/position")
    public ResponseEntity<ApiResponse<UserImageResponse>> updateBackgroundPosition(
            @RequestParam Double y) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateBackgroundPosition(y)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<List<UserSummaryResponse>>>> searchUsers(
            @RequestParam String keyword,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userSearchService.searchUsers(keyword, pageable)));
    }
}
