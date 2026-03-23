package com.leafy.socialfeedservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.leafy.socialfeedservice.dto.request.comment.CreateCommentRequest;
import com.leafy.socialfeedservice.dto.request.comment.UpdateCommentRequest;
import com.leafy.socialfeedservice.dto.response.comment.CommentResponse;
import com.leafy.socialfeedservice.service.comment.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comment management APIs")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "Create comment")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(@Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(commentService.createComment(request)));
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Update comment")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable String commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(commentService.updateComment(commentId, request)));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete comment (soft)")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable String commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "Get root comments by post")
    public ResponseEntity<ApiResponse<PageResponse<List<CommentResponse>>>> getRootCommentsByPost(
            @PathVariable String postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(commentService.getRootCommentsByPost(postId, page, size)));
    }

    @GetMapping("/{commentId}/replies")
    @Operation(summary = "Get all replies by comment")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getRepliesByComment(
            @PathVariable String commentId) {
        return ResponseEntity.ok(ApiResponse.success(commentService.getRepliesByComment(commentId)));
    }
}
