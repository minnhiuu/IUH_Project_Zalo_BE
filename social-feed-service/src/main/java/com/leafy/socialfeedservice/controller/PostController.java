package com.leafy.socialfeedservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.leafy.socialfeedservice.dto.request.post.CreatePostRequest;
import com.leafy.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.leafy.socialfeedservice.dto.response.post.PostResponse;
import com.leafy.socialfeedservice.service.post.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Post management APIs")
public class PostController {

    private final PostService postService;

    @PostMapping
    @Operation(summary = "Create post")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(postService.createPost(request)));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get post by id")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(@PathVariable String postId) {
        return ResponseEntity.ok(ApiResponse.success(postService.getPostById(postId)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my posts")
    public ResponseEntity<ApiResponse<PageResponse<List<PostResponse>>>> getMyPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(postService.getMyPosts(page, size)));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "Update post")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable String postId,
            @Valid @RequestBody UpdatePostRequest request) {
        return ResponseEntity.ok(ApiResponse.success(postService.updatePost(postId, request)));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete post (soft)")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable String postId) {
        postService.deletePost(postId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
