package com.bondhub.searchservice.service.interactionfeature;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.messageservice.ChatInteractionFeatureSnapshotResponse;
import com.bondhub.common.dto.client.socialfeedservice.SocialInteractionFeatureSnapshotResponse;
import com.bondhub.searchservice.client.MessageServiceClient;
import com.bondhub.searchservice.client.SocialFeedServiceClient;
import com.bondhub.searchservice.dto.response.UserInteractionFeatureRebuildResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserInteractionFeatureRebuildServiceImpl implements UserInteractionFeatureRebuildService {

    private static final int DEFAULT_SINCE_DAYS = 30;
    private static final int DEFAULT_SOURCE_LIMIT = 5_000;
    private static final int MAX_SOURCE_LIMIT = 10_000;

    MessageServiceClient messageServiceClient;
    SocialFeedServiceClient socialFeedServiceClient;
    UserInteractionFeatureService userInteractionFeatureService;

    @Override
    public UserInteractionFeatureRebuildResponse rebuild(int sinceDays, int sourceLimit) {
        long startedAt = System.nanoTime();
        int boundedSinceDays = sinceDays > 0 ? sinceDays : DEFAULT_SINCE_DAYS;
        int boundedSourceLimit = Math.min(Math.max(sourceLimit > 0 ? sourceLimit : DEFAULT_SOURCE_LIMIT, 1), MAX_SOURCE_LIMIT);

        List<ChatInteractionFeatureSnapshotResponse> chatSnapshots =
                fetchChatSnapshots(boundedSinceDays, boundedSourceLimit);
        List<SocialInteractionFeatureSnapshotResponse> socialSnapshots =
                fetchSocialSnapshots(boundedSinceDays, boundedSourceLimit);

        chatSnapshots.forEach(userInteractionFeatureService::upsertChatSnapshot);
        socialSnapshots.forEach(userInteractionFeatureService::upsertSocialSnapshot);

        int upsertedCount = chatSnapshots.size() + socialSnapshots.size();
        long tookMs = elapsedMs(startedAt);
        log.info("User interaction feature rebuild completed sinceDays={}, sourceLimit={}, chatSnapshots={}, socialSnapshots={}, upserted={}, tookMs={}",
                boundedSinceDays,
                boundedSourceLimit,
                chatSnapshots.size(),
                socialSnapshots.size(),
                upsertedCount,
                tookMs);

        return UserInteractionFeatureRebuildResponse.builder()
                .sinceDays(boundedSinceDays)
                .sourceLimit(boundedSourceLimit)
                .chatSnapshotCount(chatSnapshots.size())
                .socialSnapshotCount(socialSnapshots.size())
                .upsertedFeatureCount(upsertedCount)
                .tookMs(tookMs)
                .build();
    }

    private List<ChatInteractionFeatureSnapshotResponse> fetchChatSnapshots(int sinceDays, int sourceLimit) {
        try {
            ApiResponse<List<ChatInteractionFeatureSnapshotResponse>> response =
                    messageServiceClient.getSearchInteractionFeatureSnapshot(sinceDays, sourceLimit);
            return response != null && response.data() != null ? response.data() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Chat interaction feature snapshot fetch failed sinceDays={}, sourceLimit={}, reason={}",
                    sinceDays, sourceLimit, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SocialInteractionFeatureSnapshotResponse> fetchSocialSnapshots(int sinceDays, int sourceLimit) {
        try {
            ApiResponse<List<SocialInteractionFeatureSnapshotResponse>> response =
                    socialFeedServiceClient.getSearchInteractionFeatureSnapshot(sinceDays, sourceLimit);
            return response != null && response.data() != null ? response.data() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Social interaction feature snapshot fetch failed sinceDays={}, sourceLimit={}, reason={}",
                    sinceDays, sourceLimit, e.getMessage());
            return Collections.emptyList();
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
