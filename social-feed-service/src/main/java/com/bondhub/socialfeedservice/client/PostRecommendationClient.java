package com.bondhub.socialfeedservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Feign client for communicating with the post-recommendation-service.
 * Used to trigger user vector re-computation after seeding initial interests.
 */
@FeignClient(name = "post-recommendation-service")
public interface PostRecommendationClient {

    /**
     * Triggers a manual re-vectorization for the given user.
     * Blends the user's baseline vector with their recent interactions
     * and upserts the result back to Qdrant.
     *
     * @param userId the ID of the user to re-vectorize
     */
    @PostMapping("/internal/recommendations/users/{userId}/vectorize")
    void revectorizeUser(@PathVariable("userId") String userId);
}
