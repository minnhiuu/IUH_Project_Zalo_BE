package com.bondhub.friendservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.friendservice.dto.request.FriendRequestSendRequest;
import com.bondhub.friendservice.dto.response.*;
import com.bondhub.friendservice.service.friendship.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friendships")
@RequiredArgsConstructor
@Tag(name = "Friendship", description = "Friend management APIs")
public class FriendshipController {
    private final FriendshipService friendshipService;

    @PostMapping("/requests")
    @Operation(summary = "Send friend request", description = "Send a friend request to another user")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> sendFriendRequest(
            @Valid @RequestBody FriendRequestSendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(friendshipService.sendFriendRequest(request)));
    }

    @PutMapping("/requests/{friendshipId}/accept")
    @Operation(summary = "Accept friend request", description = "Accept a pending friend request")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> acceptFriendRequest(
            @PathVariable String friendshipId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.acceptFriendRequest(friendshipId)));
    }

    @PutMapping("/requests/{friendshipId}/decline")
    @Operation(summary = "Decline friend request", description = "Decline a pending friend request")
    public ResponseEntity<ApiResponse<Void>> declineFriendRequest(
            @PathVariable String friendshipId) {
        friendshipService.declineFriendRequest(friendshipId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PutMapping("/requests/{friendshipId}/cancel")
    @Operation(summary = "Cancel friend request", description = "Cancel a sent friend request")
    public ResponseEntity<ApiResponse<Void>> cancelFriendRequest(
            @PathVariable String friendshipId) {
        friendshipService.cancelFriendRequest(friendshipId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @DeleteMapping("/friends/{friendId}")
    @Operation(summary = "Unfriend", description = "Remove a friend from your friend list")
    public ResponseEntity<ApiResponse<Void>> unfriend(@PathVariable String friendId) {
        friendshipService.unfriend(friendId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @GetMapping("/requests/received")
    @Operation(summary = "Get received friend requests", description = "Get all pending friend requests received with pagination")
    public ResponseEntity<ApiResponse<PageResponse<List<FriendRequestResponse>>>> getReceivedFriendRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getReceivedFriendRequests(pageable)));
    }

    @GetMapping("/requests/sent")
    @Operation(summary = "Get sent friend requests", description = "Get all pending friend requests sent with pagination")
    public ResponseEntity<ApiResponse<PageResponse<List<FriendRequestResponse>>>> getSentFriendRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getSentFriendRequests(pageable)));
    }

    @GetMapping("/friends")
    @Operation(summary = "Get my friends", description = "Get all accepted friends with pagination")
    public ResponseEntity<ApiResponse<PageResponse<List<FriendResponse>>>> getMyFriends(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getMyFriends(pageable)));
    }

    @GetMapping("/status/{userId}")
    @Operation(summary = "Check friendship status", description = "Check friendship status with a specific user")
    public ResponseEntity<ApiResponse<FriendshipStatusResponse>> checkFriendshipStatus(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.checkFriendshipStatus(userId)));
    }

    @GetMapping("/mutual/{userId}")
    @Operation(summary = "Get mutual friends", description = "Get list of mutual friends with a specific user")
    public ResponseEntity<ApiResponse<MutualFriendsResponse>> getMutualFriends(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getMutualFriends(userId)));
    }

    @GetMapping("/mutual/{userId}/count")
    @Operation(summary = "Count mutual friends", description = "Get count of mutual friends with a specific user")
    public ResponseEntity<ApiResponse<Integer>> countMutualFriends(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getMutualFriendsCount(userId)));
    }
}
