package com.bondhub.socialfeedservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.response.interaction.UserInteractionResponse;
import com.bondhub.socialfeedservice.service.userinteraction.UserInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/interactions")
@RequiredArgsConstructor
@Tag(name = "User Interactions", description = "User interaction history APIs")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInteractionController {

    UserInteractionService userInteractionService;

    @GetMapping("/me")
    @Operation(summary = "Get my interaction history")
    public ResponseEntity<ApiResponse<PageResponse<List<UserInteractionResponse>>>> getMyInteractions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(userInteractionService.getMyInteractions(page, size)));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Get interaction history for a post")
    public ResponseEntity<ApiResponse<PageResponse<List<UserInteractionResponse>>>> getInteractionsByPost(
            @PathVariable String postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(userInteractionService.getInteractionsByPost(postId, page, size)));
    }

    @PostMapping("/posts/{postId}/view")
    @Operation(summary = "Record a VIEW interaction for a post (idempotent — at most one record per user)")
    public ResponseEntity<ApiResponse<Void>> recordView(@PathVariable String postId) {
        userInteractionService.recordView(postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

