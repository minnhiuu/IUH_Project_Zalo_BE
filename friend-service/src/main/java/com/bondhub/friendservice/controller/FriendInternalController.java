package com.bondhub.friendservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.friendservice.service.friendship.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/internal/friends")
@RequiredArgsConstructor
@Tag(name = "Friendship Internal", description = "Internal APIs for Friend Management")
public class FriendInternalController {

    private final FriendshipService friendshipService;

    @GetMapping("/user/{userId}/friend-ids")
    @Operation(summary = "Get friend IDs (Internal)", description = "Internal API to fetch all friend IDs for a user")
    public ResponseEntity<ApiResponse<Set<String>>> getFriendIdsInternal(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getFriendIds(userId)));
    }
}
