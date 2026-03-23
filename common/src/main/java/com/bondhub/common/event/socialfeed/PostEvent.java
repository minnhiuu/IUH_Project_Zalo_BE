package com.bondhub.common.event.socialfeed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

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
        List<String> hashtags,
        @JsonProperty("post_type")
        String postType,
        @JsonProperty("shared_post_id")
        String sharedPostId,
        @JsonProperty("original_author_id")
        String originalAuthorId,
        @JsonProperty("root_post_id")
        String rootPostId,
        String visibility,
        @JsonProperty("uploaded_at")
        LocalDateTime uploadedAt,
        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
}
