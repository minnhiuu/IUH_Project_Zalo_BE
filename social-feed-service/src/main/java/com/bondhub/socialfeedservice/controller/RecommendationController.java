package com.bondhub.socialfeedservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.response.post.PostResponse;
import com.bondhub.socialfeedservice.service.recommendation.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final SecurityUtil securityUtil;

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getRecommendedFeed(
            @RequestParam(defaultValue = "20") int size) {

        String userId = securityUtil.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(recommendationService.getRecommendedFeed(userId, size)));
    }

    @GetMapping("/reels")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getRecommendedReels(
            @RequestParam(defaultValue = "20") int size) {

        String userId = securityUtil.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(recommendationService.getRecommendedReels(userId, size)));
    }
}
