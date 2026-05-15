package com.bondhub.socialfeedservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.socialfeedservice.client.PostRecommendationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/recommendations")
@RequiredArgsConstructor
public class AdminRecommendationController {

    private final PostRecommendationClient postRecommendationClient;

    @PostMapping("/users/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> syncAllUserProfiles() {
        postRecommendationClient.syncAllUserProfiles();
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
