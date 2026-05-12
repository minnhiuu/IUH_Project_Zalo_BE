package com.bondhub.socialfeedservice.controller.internal;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.socialfeedservice.RecentAuthorInteractionRequest;
import com.bondhub.common.dto.client.socialfeedservice.RecentAuthorInteractionResponse;
import com.bondhub.socialfeedservice.service.userinteraction.UserInteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/social-feed")
@RequiredArgsConstructor
public class InternalSocialFeedSearchContextController {

    private final UserInteractionService userInteractionService;

    @PostMapping("/recent-author-interactions")
    public ResponseEntity<ApiResponse<List<RecentAuthorInteractionResponse>>> getRecentAuthorInteractions(
            @Valid @RequestBody RecentAuthorInteractionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userInteractionService.getRecentAuthorInteractions(request)));
    }
}
