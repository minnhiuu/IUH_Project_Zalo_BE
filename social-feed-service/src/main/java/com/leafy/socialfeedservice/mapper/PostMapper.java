package com.leafy.socialfeedservice.mapper;

import com.leafy.socialfeedservice.dto.request.post.CreatePostRequest;
import com.leafy.socialfeedservice.dto.request.post.PostMediaRequest;
import com.leafy.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.leafy.socialfeedservice.dto.response.post.PostResponse;
import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.model.embedded.PostContent;
import com.leafy.socialfeedservice.model.embedded.PostMedia;
import com.leafy.socialfeedservice.model.embedded.PostStats;
import org.mapstruct.Mapper;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PostMapper {

    default PostResponse toPostResponse(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .groupId(post.getGroupId())
                .content(post.getContent())
                .media(post.getMedia())
                .postType(post.getPostType())
                .visibility(post.getVisibility())
                .stats(post.getStats())
                .uploadedAt(post.getUploadedAt())
                .updatedAt(post.getUpdatedAt())
                .version(post.getVersion())
                .isCurrent(post.isCurrent())
                .isEdited(post.isEdited())
                .build();
    }

    default Post toPost(CreatePostRequest request) {
        return Post.builder()
                .groupId(request.groupId())
                .content(PostContent.builder()
                        .title(request.title())
                        .caption(request.caption())
                        .description(request.description())
                        .hashtags(request.hashtags())
                        .build())
                .media(toMedia(request.media()))
                .postType(request.postType())
                .visibility(request.visibility())
                .stats(PostStats.builder()
                        .reactionCount(0)
                        .commentCount(0)
                        .shareCount(0)
                        .build())
                .build();
    }

    default void updatePost(Post post, UpdatePostRequest request) {
        PostContent currentContent = post.getContent() == null ? PostContent.builder().build() : post.getContent();

        post.setContent(PostContent.builder()
                .title(request.title() != null ? request.title() : currentContent.getTitle())
                .caption(request.caption() != null ? request.caption() : currentContent.getCaption())
                .description(request.description() != null ? request.description() : currentContent.getDescription())
                .hashtags(request.hashtags() != null ? request.hashtags() : currentContent.getHashtags())
                .build());

        if (request.media() != null) {
            post.setMedia(toMedia(request.media()));
        }

        if (request.visibility() != null) {
            post.setVisibility(request.visibility());
        }
    }

    default List<PostMedia> toMedia(List<PostMediaRequest> mediaRequests) {
        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return new ArrayList<>();
        }

        return mediaRequests.stream()
                .map(media -> PostMedia.builder()
                        .url(media.url())
                        .type(media.type())
                        .build())
                .toList();
    }
}
