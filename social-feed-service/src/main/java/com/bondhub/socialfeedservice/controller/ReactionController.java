package com.bondhub.socialfeedservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.socialfeedservice.dto.request.reaction.ToggleReactionRequest;
import com.bondhub.socialfeedservice.dto.response.reaction.ReactionResponse;
import com.bondhub.socialfeedservice.dto.response.reaction.ReactionStatsResponse;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import com.bondhub.socialfeedservice.service.reaction.ReactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reactions")
@RequiredArgsConstructor
@Tag(name = "Reactions", description = "Reaction management APIs")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReactionController {
    ReactionService reactionService;

    @PostMapping("/toggle")
    @Operation(summary = "Toggle reaction for post/comment")
    public ResponseEntity<ApiResponse<ReactionResponse>> toggleReaction(
            @Valid @RequestBody ToggleReactionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reactionService.toggleReaction(request)));
    }

    @DeleteMapping
    @Operation(summary = "Delete my reaction for post/comment")
    public ResponseEntity<ApiResponse<ReactionResponse>> deleteReaction(
            @RequestParam @NotBlank(message = "reaction.targetId.required") String targetId,
            @RequestParam @NotNull(message = "reaction.targetType.required") ReactionTargetType targetType) {
        return ResponseEntity.ok(ApiResponse.success(reactionService.deleteReaction(targetId, targetType)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search reactions by target type and reaction type")
    public ResponseEntity<ApiResponse<List<ReactionResponse>>> searchReactions(
            @RequestParam @NotNull(message = "reaction.targetType.required") ReactionTargetType targetType,
            @RequestParam @NotNull(message = "reaction.type.required") ReactionType reactionType) {
        return ResponseEntity.ok(ApiResponse.success(reactionService.searchReactions(targetType, reactionType)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get reaction stats by target")
    public ResponseEntity<ApiResponse<ReactionStatsResponse>> getReactionStats(
            @RequestParam @NotBlank(message = "reaction.targetId.required") String targetId,
            @RequestParam @NotNull(message = "reaction.targetType.required") ReactionTargetType targetType) {
        return ResponseEntity.ok(ApiResponse.success(reactionService.getReactionStats(targetId, targetType)));
    }
}
