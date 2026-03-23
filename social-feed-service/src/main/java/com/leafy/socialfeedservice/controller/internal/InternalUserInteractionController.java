package com.leafy.socialfeedservice.controller.internal;

import com.bondhub.common.dto.ApiResponse;
import com.leafy.socialfeedservice.dto.response.interaction.UserInteractionResponse;
import com.leafy.socialfeedservice.service.userinteraction.UserInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/interactions")
@RequiredArgsConstructor
public class InternalUserInteractionController {

    private final UserInteractionService userInteractionService;

    @GetMapping("/users/{userId}/newest")
    public ResponseEntity<ApiResponse<List<UserInteractionResponse>>> getNewestInteractionsByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(userInteractionService.getNewestInteractionsByUser(userId, limit)));
    }
}
