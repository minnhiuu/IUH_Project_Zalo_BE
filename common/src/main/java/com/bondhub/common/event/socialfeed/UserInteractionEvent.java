package com.bondhub.common.event.socialfeed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserInteractionEvent(
        @JsonProperty("user_id")
        String userId,
        @JsonProperty("post_id")
        String postId,
        @JsonProperty("interaction_type")
        InteractionType interactionType,
        float weight,
        @JsonProperty("created_at")
        Instant createdAt,
        @JsonProperty("group_id")
        String groupId
) {
}