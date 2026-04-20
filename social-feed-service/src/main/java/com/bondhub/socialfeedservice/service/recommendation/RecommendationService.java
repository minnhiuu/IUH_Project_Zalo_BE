package com.bondhub.socialfeedservice.service.recommendation;

import com.bondhub.socialfeedservice.dto.response.post.PostResponse;

import java.util.List;

public interface RecommendationService {

    /**
     * Returns a personalized FEED_SHARE recommendation feed for the given user.
     * Posts the user has already viewed are excluded by the upstream RRF pipeline.
     *
     * @param userId the authenticated user's ID
     * @param size   number of posts to return
     * @return ordered list of hydrated {@link PostResponse} objects
     */
    List<PostResponse> getRecommendedFeed(String userId, int size);

    /**
     * Returns a personalized REEL recommendation feed for the given user.
     * Posts the user has already viewed are excluded by the upstream RRF pipeline.
     *
     * @param userId the authenticated user's ID
     * @param size   number of reels to return
     * @return ordered list of hydrated {@link PostResponse} objects (REEL type only)
     */
    List<PostResponse> getRecommendedReels(String userId, int size);
}
