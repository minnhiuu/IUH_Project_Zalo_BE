package com.bondhub.searchservice.client;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.socialfeedservice.RecentAuthorInteractionRequest;
import com.bondhub.common.dto.client.socialfeedservice.RecentAuthorInteractionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "social-feed-service")
public interface SocialFeedServiceClient {

    @PostMapping("/internal/social-feed/recent-author-interactions")
    ApiResponse<List<RecentAuthorInteractionResponse>> getRecentAuthorInteractions(
            @RequestBody RecentAuthorInteractionRequest request);
}
