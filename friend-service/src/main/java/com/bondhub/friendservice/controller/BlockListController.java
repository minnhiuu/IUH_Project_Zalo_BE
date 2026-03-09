package com.bondhub.friendservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.friendservice.dto.request.BlockUserRequest;
import com.bondhub.friendservice.dto.request.UpdateBlockPreferenceRequest;
import com.bondhub.friendservice.dto.response.BlockedUserDetailResponse;
import com.bondhub.friendservice.dto.response.BlockedUserResponse;
import com.bondhub.friendservice.service.blocklist.BlockListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockListController {

    private final BlockListService blockListService;

    @PostMapping
    public ResponseEntity<ApiResponse<BlockedUserResponse>> blockUser(
            @Valid @RequestBody BlockUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(blockListService.blockUser(request)));
    }

    @DeleteMapping("/{blockedUserId}")
    public ResponseEntity<ApiResponse<Void>> unblockUser(
            @PathVariable String blockedUserId) {
        blockListService.unblockUser(blockedUserId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PatchMapping("/{blockedUserId}/preferences")
    public ResponseEntity<ApiResponse<BlockedUserResponse>> updateBlockPreference(
            @PathVariable String blockedUserId,
            @Valid @RequestBody UpdateBlockPreferenceRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(blockListService.updateBlockPreference(blockedUserId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> getMyBlockedUsers() {
        return ResponseEntity.ok(ApiResponse.success(blockListService.getMyBlockedUsers()));
    }

    @GetMapping("/details")
    public ResponseEntity<ApiResponse<List<BlockedUserDetailResponse>>> getMyBlockedUsersWithDetails() {
        return ResponseEntity.ok(ApiResponse.success(blockListService.getMyBlockedUsersWithDetails()));
    }

    @GetMapping("/{userId}/check")
    public ResponseEntity<ApiResponse<Boolean>> isUserBlocked(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(blockListService.isUserBlocked(userId)));
    }

    @GetMapping("/{blockedUserId}")
    public ResponseEntity<ApiResponse<BlockedUserResponse>> getBlockDetails(
            @PathVariable String blockedUserId) {
        return ResponseEntity.ok(ApiResponse.success(blockListService.getBlockDetails(blockedUserId)));
    }
}
