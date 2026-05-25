package com.bondhub.socialfeedservice.controller.internal;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.socialfeedservice.SocialInteractionFeatureSnapshotResponse;
import com.bondhub.socialfeedservice.service.userinteraction.UserInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/social-feed")
@RequiredArgsConstructor
public class InternalSocialFeedSearchContextController {

    private final UserInteractionService userInteractionService;

    @GetMapping("/search-interaction-features/snapshot")
    public ResponseEntity<ApiResponse<List<SocialInteractionFeatureSnapshotResponse>>> getSearchInteractionFeatureSnapshot(
            @RequestParam(defaultValue = "30") int sinceDays,
            @RequestParam(defaultValue = "5000") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                userInteractionService.getSearchInteractionFeatureSnapshot(sinceDays, limit)));
    }
}
