package com.bondhub.searchservice.service.interactionfeature;

import com.bondhub.common.dto.client.messageservice.ChatInteractionFeatureSnapshotResponse;
import com.bondhub.common.dto.client.socialfeedservice.SocialInteractionFeatureSnapshotResponse;
import com.bondhub.common.event.search.ChatInteractionOccurredEvent;
import com.bondhub.common.event.search.SocialFeedInteractionOccurredEvent;

import java.util.List;

public interface UserInteractionFeatureService {

    void recordChatInteraction(ChatInteractionOccurredEvent event);

    void recordSocialFeedInteraction(SocialFeedInteractionOccurredEvent event);

    void upsertChatSnapshot(ChatInteractionFeatureSnapshotResponse snapshot);

    void upsertSocialSnapshot(SocialInteractionFeatureSnapshotResponse snapshot);

    int upsertChatSnapshots(List<ChatInteractionFeatureSnapshotResponse> snapshots);

    int upsertSocialSnapshots(List<SocialInteractionFeatureSnapshotResponse> snapshots);
}
