package com.bondhub.common.event.socialfeed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostEvent(
        @JsonProperty("post_id")
        String postId,
        @JsonProperty("author_id")
        String authorId,
        @JsonProperty("group_id")
        String groupId,
        String title,
        String caption,
        String description,
        String visibility,
        @JsonProperty("uploaded_at")
        LocalDateTime uploadedAt,
        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
}
