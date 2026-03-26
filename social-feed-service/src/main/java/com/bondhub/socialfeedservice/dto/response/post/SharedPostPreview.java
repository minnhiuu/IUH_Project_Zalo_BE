package com.bondhub.socialfeedservice.dto.response.post;

import com.bondhub.socialfeedservice.model.embedded.PostContent;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import lombok.Builder;

import java.util.List;

@Builder
public record SharedPostPreview(
        String postId,
        AuthorInfo authorInfo,
        PostContent content,
        List<PostMedia> media
) {
}
