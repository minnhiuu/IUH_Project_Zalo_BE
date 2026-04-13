package com.bondhub.socialfeedservice.service.recommendation;

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

        List<String> postIds = postRecommendationClient
                .getRrfFeedPostIds(userId, size)
                .data();

        if (postIds == null || postIds.isEmpty()) {
            log.info("[Recommendation] feed: no post IDs returned for userId={}", userId);
            return List.of();
        }

        List<PostResponse> posts = postService.getPostsByIds(postIds, userId);
        log.info("[Recommendation] feed: hydrated {} posts for userId={}", posts.size(), userId);
        return posts;
    }

    @Override
    public List<PostResponse> getRecommendedReels(String userId, int size) {
        log.info("[Recommendation] reels request: userId={}, size={}", userId, size);

        List<String> postIds = postRecommendationClient
                .getRrfReelPostIds(userId, size)
                .data();

        if (postIds == null || postIds.isEmpty()) {
            log.info("[Recommendation] reels: no post IDs returned for userId={}", userId);
            return List.of();
        }

        List<PostResponse> posts = postService.getPostsByIds(postIds, userId);
        log.info("[Recommendation] reels: hydrated {} reels for userId={}", posts.size(), userId);
        return posts;
    }
}
