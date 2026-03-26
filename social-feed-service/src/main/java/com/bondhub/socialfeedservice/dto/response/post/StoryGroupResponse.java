package com.bondhub.socialfeedservice.dto.response.post;

import lombok.Builder;

import java.util.List;

/**
 * Groups all active stories for a single author into one response object.
 * The strip shows one card per group; the viewer navigates within the group.
 */
@Builder
public record StoryGroupResponse(
        AuthorInfo authorInfo,
        List<PostResponse> stories
) {
}
