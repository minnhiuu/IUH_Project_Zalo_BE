package com.bondhub.socialfeedservice.client;

import com.bondhub.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import com.bondhub.socialfeedservice.dto.request.user.UserInterestSeedUpdateRequest;

import java.util.List;

/**
 * Feign client for communicating with the post-recommendation-service.
 */
@FeignClient(name = "post-recommendation-service")
public interface PostRecommendationClient {

    /**
     * Triggers a manual re-vectorization for the given user.
     *
     * @param userId the ID of the user to re-vectorize
     */
    @PostMapping("/internal/recommendations/users/{userId}/vectorize")
    void revectorizeUser(@PathVariable("userId") String userId);

    /**
     * Seeds initial interests for a user and rebuilds their baseline vector.
     *
     * @param userId the ID of the user
     * @param request the interests to seed
     */
    @PostMapping("/internal/recommendations/users/{userId}/seed")
    void seedUserInterests(@PathVariable("userId") String userId, @RequestBody UserInterestSeedUpdateRequest request);

    /**
     * Syncs a user profile from user-service to Qdrant and rebuilds baseline vector.
     *
     * @param userId the ID of the user
     */
    @PostMapping("/internal/recommendations/users/{userId}/sync")
    void syncUserProfile(@PathVariable("userId") String userId);

    /**
     * Syncs all user profiles from user-service to Qdrant and rebuilds baseline vectors.
     */
    @PostMapping("/internal/recommendations/users/sync-all")
    void syncAllUserProfiles();

    /**
     * Returns RRF-ranked FEED_SHARE post IDs for the given user.
     * The list excludes posts the user has already viewed (VIEW interactions).
     *
     * @param userId the user ID to generate recommendations for
     * @param n      number of post IDs to return (1–100)
     */
    @GetMapping("/internal/rrf/feed/{userId}")
    ApiResponse<List<String>> getRrfFeedPostIds(
            @PathVariable("userId") String userId,
            @RequestParam(defaultValue = "20") int n);

    /**
     * Returns RRF-ranked REEL post IDs for the given user.
     * Uses REEL-flow source weights (trending boosted) and VIEW exclusion.
     *
     * @param userId the user ID to generate recommendations for
     * @param n      number of post IDs to return (1–100)
     */
    @GetMapping("/internal/rrf/reels/{userId}")
    ApiResponse<List<String>> getRrfReelPostIds(
            @PathVariable("userId") String userId,
            @RequestParam(defaultValue = "20") int n);
}
