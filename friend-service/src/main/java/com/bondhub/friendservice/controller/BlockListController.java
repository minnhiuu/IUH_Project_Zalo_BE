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

/**
 * REST controller exposing endpoints for managing user block relationships.
 * All endpoints require the caller to be authenticated.
 * Base path: {@code /blocks}
 */
@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockListController {

    private final BlockListService blockListService;

    /**
     * Block a user.
     *
     * @param request the block request containing the target user ID and optional preferences
     * @return {@code 201 Created} with the created block record
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BlockedUserResponse>> blockUser(
            @Valid @RequestBody BlockUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(blockListService.blockUser(request)));
    }

    /**
     * Unblock a previously blocked user.
     *
     * @param blockedUserId the ID of the user to unblock
     * @return {@code 200 OK} with no data
     */
    @DeleteMapping("/{blockedUserId}")
    public ResponseEntity<ApiResponse<Void>> unblockUser(
            @PathVariable String blockedUserId) {
        blockListService.unblockUser(blockedUserId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    /**
     * Update blocking preferences (message, call, story) for a specific blocked user.
     *
     * @param blockedUserId the ID of the blocked user
     * @param request       the fields to update
     * @return {@code 200 OK} with the updated block record
     */
    @PatchMapping("/{blockedUserId}/preferences")
    public ResponseEntity<ApiResponse<BlockedUserResponse>> updateBlockPreference(
            @PathVariable String blockedUserId,
            @Valid @RequestBody UpdateBlockPreferenceRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(blockListService.updateBlockPreference(blockedUserId, request)));
    }

    /**
     * Get all users blocked by the currently authenticated user (basic info).
     *
     * @return {@code 200 OK} with the list of blocked users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> getMyBlockedUsers() {
        return ResponseEntity.ok(ApiResponse.success(blockListService.getMyBlockedUsers()));
    }

    /**
     * Get all users blocked by the currently authenticated user with full profile details.
     *
     * @return {@code 200 OK} with the detailed list of blocked users
     */
    @GetMapping("/details")
    public ResponseEntity<ApiResponse<List<BlockedUserDetailResponse>>> getMyBlockedUsersWithDetails() {
        return ResponseEntity.ok(ApiResponse.success(blockListService.getMyBlockedUsersWithDetails()));
    }

    /**
     * Check whether the currently authenticated user has blocked a given user.
     *
     * @param userId the ID of the user to check
     * @return {@code 200 OK} with {@code true} if blocked, {@code false} otherwise
     */
    @GetMapping("/{userId}/check")
    public ResponseEntity<ApiResponse<Boolean>> isUserBlocked(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(blockListService.isUserBlocked(userId)));
    }

    /**
     * Get the block record details for a specific blocked user.
     *
     * @param blockedUserId the ID of the blocked user
     * @return {@code 200 OK} with the block details, or {@code null} if not blocked
     */
    @GetMapping("/{blockedUserId}")
    public ResponseEntity<ApiResponse<BlockedUserResponse>> getBlockDetails(
            @PathVariable String blockedUserId) {
        return ResponseEntity.ok(ApiResponse.success(blockListService.getBlockDetails(blockedUserId)));
    }
}
