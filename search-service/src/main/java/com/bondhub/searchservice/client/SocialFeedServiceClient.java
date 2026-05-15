package com.bondhub.searchservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.socialfeedservice.SocialInteractionFeatureSnapshotResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "social-feed-service")
public interface SocialFeedServiceClient {

    @GetMapping("/internal/social-feed/search-interaction-features/snapshot")
    ApiResponse<List<SocialInteractionFeatureSnapshotResponse>> getSearchInteractionFeatureSnapshot(
            @RequestParam("sinceDays") int sinceDays,
            @RequestParam("limit") int limit);
}
