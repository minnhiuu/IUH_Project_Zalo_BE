package com.bondhub.socialfeedservice.service.recommendation;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.client.PostRecommendationClient;
import com.bondhub.socialfeedservice.dto.response.post.PostResponse;
import com.bondhub.socialfeedservice.service.post.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private final PostRecommendationClient postRecommendationClient;
    private final PostService postService;

    @Override
    public List<PostResponse> getRecommendedFeed(String userId, int size) {
        log.info("[Recommendation] feed request: userId={}, size={}", userId, size);

        List<String> postIds;
        try {
            ApiResponse<List<String>> response = postRecommendationClient.getRrfFeedPostIds(userId, size);
            postIds = response != null ? response.data() : null;
        } catch (Exception e) {
            log.warn("[Recommendation] feed: recommendation-service failed for userId={}, falling back to MongoDB",
                    userId, e);
            return fallbackFeedFromMongo(userId, size);
        }

        if (postIds == null || postIds.isEmpty()) {
            log.info("[Recommendation] feed: no post IDs returned for userId={}, falling back to MongoDB", userId);
            return fallbackFeedFromMongo(userId, size);
        }

        List<PostResponse> posts = postService.getPostsByIds(postIds, userId);
        log.info("[Recommendation] feed: hydrated {} posts for userId={}", posts.size(), userId);
        return posts;
    }

    @Override
    public List<PostResponse> getRecommendedReels(String userId, int size) {
        log.info("[Recommendation] reels request: userId={}, size={}", userId, size);

        List<String> postIds;
        try {
            ApiResponse<List<String>> response = postRecommendationClient.getRrfReelPostIds(userId, size);
            postIds = response != null ? response.data() : null;
        } catch (Exception e) {
            log.warn("[Recommendation] reels: recommendation-service failed for userId={}, falling back to MongoDB",
                    userId, e);
            return fallbackReelsFromMongo(userId, size);
        }

        if (postIds == null || postIds.isEmpty()) {
            log.info("[Recommendation] reels: no post IDs returned for userId={}, falling back to MongoDB", userId);
            return fallbackReelsFromMongo(userId, size);
        }

        List<PostResponse> posts = postService.getPostsByIds(postIds, userId);
        log.info("[Recommendation] reels: hydrated {} reels for userId={}", posts.size(), userId);
        return posts;
    }

    private List<PostResponse> fallbackFeedFromMongo(String userId, int size) {
        PageResponse<List<PostResponse>> page = postService.getFeedAndSharePosts(0, size);
        List<PostResponse> posts = page != null && page.data() != null ? page.data() : List.of();
        log.info("[Recommendation] feed fallback: served {} posts from MongoDB for userId={}", posts.size(), userId);
        return posts;
    }

    private List<PostResponse> fallbackReelsFromMongo(String userId, int size) {
        PageResponse<List<PostResponse>> page = postService.getReelPosts(0, size);
        List<PostResponse> posts = page != null && page.data() != null ? page.data() : List.of();
        log.info("[Recommendation] reels fallback: served {} reels from MongoDB for userId={}", posts.size(), userId);
        return posts;
    }
}
