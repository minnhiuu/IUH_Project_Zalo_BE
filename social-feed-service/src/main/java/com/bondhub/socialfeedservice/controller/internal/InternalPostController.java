package com.bondhub.socialfeedservice.controller.internal;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.socialfeedservice.dto.response.post.PostResponse;
import com.bondhub.socialfeedservice.service.post.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal-only REST controller consumed by the post-recommendation-service.
 *
 * <p>All endpoints under {@code /internal/posts} are <strong>not</strong> exposed
 * through the API Gateway and are only reachable within the Docker/k8s network.
 *
 * <p>Endpoint added here:
 * <ul>
 *   <li>{@code GET /internal/posts/by-authors} — fetch the most-recent posts
 *       authored by a supplied list of user IDs and an optional PostType filter.
 *       Used by the recommendation service to build the {@code friend_posts}
 *       and {@code peer_posts} candidate streams.</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
public class InternalPostController {

    private final PostService postService;

    /**
     * Return the latest posts written by the supplied author IDs.
     *
     * @param authorIds Comma-separated list of user IDs (e.g. {@code ?authorIds=abc,def}).
     * @param postType  Optional PostType filter — {@code FEED}, {@code SHARE}, or {@code REEL}.
     *                  When omitted, all active post types are returned.
     * @param limit     Maximum number of posts to return per author (default 10, max 50).
     */
    @GetMapping("/by-authors")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getPostsByAuthors(
            @RequestParam List<String> authorIds,
            @RequestParam(required = false) String postType,
            @RequestParam(defaultValue = "10") int limit) {

        int safeLimit = Math.min(limit, 50);
        List<PostResponse> posts = postService.getPostsByAuthors(authorIds, postType, safeLimit);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }
}
