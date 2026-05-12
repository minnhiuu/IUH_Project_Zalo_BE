package com.bondhub.searchservice.service.interactionfeature;

import com.bondhub.common.event.search.ChatInteractionOccurredEvent;
import com.bondhub.common.event.search.SocialFeedInteractionOccurredEvent;

public interface UserInteractionFeatureService {

    void recordChatInteraction(ChatInteractionOccurredEvent event);

    void recordSocialFeedInteraction(SocialFeedInteractionOccurredEvent event);
}
