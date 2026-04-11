package com.bondhub.friendservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.friendservice.graph.service.GraphFriendService;
import com.bondhub.friendservice.model.FriendShip;
import com.bondhub.friendservice.model.enums.FriendStatus;
import com.bondhub.friendservice.repository.FriendShipRepository;
import com.bondhub.friendservice.service.friendship.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/internal/friends")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Friendship Internal", description = "Internal APIs for Friend Management")
public class FriendInternalController {

    private final FriendshipService friendshipService;
    private final FriendShipRepository friendShipRepository;
    private final GraphFriendService graphFriendService;

    @GetMapping("/user/{userId}/friend-ids")
    @Operation(summary = "Get friend IDs (Internal)", description = "Internal API to fetch all friend IDs for a user")
    public ResponseEntity<ApiResponse<Set<String>>> getFriendIdsInternal(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getFriendIds(userId)));
    }

    @PostMapping("/sync-neo4j")
    @Operation(summary = "Sync existing friendships to Neo4j", description = "One-time migration of all ACCEPTED friendships from MongoDB to Neo4j")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncFriendshipsToNeo4j() {
        log.info("Starting Neo4j sync for all ACCEPTED friendships from MongoDB...");
        List<FriendShip> acceptedFriendships = friendShipRepository.findAllByFriendStatus(FriendStatus.ACCEPTED);
        int synced = 0;
        int failed = 0;
        for (FriendShip fs : acceptedFriendships) {
            try {
                graphFriendService.createFriendRelationship(fs.getRequested(), fs.getReceived());
                synced++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to sync friendship {} -> {}: {}", fs.getRequested(), fs.getReceived(), e.getMessage());
            }
        }
        log.info("Neo4j sync complete: total={}, synced={}, failed={}", acceptedFriendships.size(), synced, failed);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "total", acceptedFriendships.size(),
                "synced", synced,
                "failed", failed
        )));
    }
}
