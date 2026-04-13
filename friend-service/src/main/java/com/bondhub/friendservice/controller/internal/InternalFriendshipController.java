package com.bondhub.friendservice.controller.internal;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.friendservice.service.friendship.FriendshipService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal controller for service-to-service calls.
 * All paths under /internal/ are permitted without authentication
 * by the friend-service SecurityConfig.
 */
@Hidden
@RestController
@RequestMapping("/internal/friendships")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalFriendshipController {

    FriendshipService friendshipService;

    /**
     * Returns the IDs of all accepted friends for the given userId.
     * Called by post-recommendation-service — no JWT required.
     *
     * @param userId the user whose friend list is requested
     * @param size   maximum number of friend IDs to return (default 50, max 200)
     */
    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<List<String>>> getFriendIds(
            @RequestParam String userId,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(size, 200);
        List<String> friendIds = friendshipService.getFriendIds(userId, safeSize);
        return ResponseEntity.ok(ApiResponse.success(friendIds));
    }
}
