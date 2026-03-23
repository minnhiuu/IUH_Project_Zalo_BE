package com.leafy.socialfeedservice.mapper;

import com.leafy.socialfeedservice.dto.request.post.CreatePostRequest;
import com.leafy.socialfeedservice.dto.request.post.LocationInfoRequest;
import com.leafy.socialfeedservice.dto.request.post.PostContentRequest;
import com.leafy.socialfeedservice.dto.request.post.PostMediaRequest;
import com.leafy.socialfeedservice.dto.request.post.StoryElementRequest;
import com.leafy.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.leafy.socialfeedservice.dto.response.post.PostResponse;
import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.model.embedded.LocationInfo;
import com.leafy.socialfeedservice.model.embedded.PostContent;
import com.leafy.socialfeedservice.model.embedded.PostMedia;
import com.leafy.socialfeedservice.model.embedded.PostStats;
import com.leafy.socialfeedservice.model.embedded.StoryElement;
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
                .sharedPostId(post.getSharedPostId())
                .originalAuthorId(post.getOriginalAuthorId())
                .sharedCaption(post.getSharedCaption())
                .rootPostId(post.getRootPostId())
                .expiresAt(post.getExpiresAt())
                .musicId(post.getMusicId())
                .viewerIds(post.getViewerIds())
                .location(post.getLocation())
                .elements(post.getElements())
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
            .sharedPostId(request.sharedPostId())
            .sharedCaption(toPostContent(request.sharedCaption()))
            .expiresAt(request.expiresAt())
            .musicId(request.musicId())
            .viewerIds(request.viewerIds() == null ? new ArrayList<>() : new ArrayList<>(request.viewerIds()))
            .location(toLocation(request.location()))
            .elements(toStoryElements(request.elements()))
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
        PostContent currentSharedCaption = post.getSharedCaption() == null ? PostContent.builder().build() : post.getSharedCaption();

        post.setContent(PostContent.builder()
                .title(request.title() != null ? request.title() : currentContent.getTitle())
                .caption(request.caption() != null ? request.caption() : currentContent.getCaption())
                .description(request.description() != null ? request.description() : currentContent.getDescription())
                .hashtags(request.hashtags() != null ? request.hashtags() : currentContent.getHashtags())
                .build());

        if (request.sharedCaption() != null) {
            post.setSharedCaption(mergePostContent(currentSharedCaption, request.sharedCaption()));
        }

        if (request.media() != null) {
            post.setMedia(toMedia(request.media()));
        }

        if (request.expiresAt() != null) {
            post.setExpiresAt(request.expiresAt());
        }

        if (request.musicId() != null) {
            post.setMusicId(request.musicId());
        }

        if (request.viewerIds() != null) {
            post.setViewerIds(new ArrayList<>(request.viewerIds()));
        }

        if (request.location() != null) {
            post.setLocation(toLocation(request.location()));
        }

        if (request.elements() != null) {
            post.setElements(toStoryElements(request.elements()));
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

    default PostContent toPostContent(PostContentRequest request) {
        if (request == null) {
            return null;
        }

        return PostContent.builder()
                .title(request.title())
                .caption(request.caption())
                .description(request.description())
                .hashtags(request.hashtags())
                .build();
    }

    default PostContent mergePostContent(PostContent currentContent, PostContentRequest request) {
        return PostContent.builder()
                .title(request.title() != null ? request.title() : currentContent.getTitle())
                .caption(request.caption() != null ? request.caption() : currentContent.getCaption())
                .description(request.description() != null ? request.description() : currentContent.getDescription())
                .hashtags(request.hashtags() != null ? request.hashtags() : currentContent.getHashtags())
                .build();
    }

    default LocationInfo toLocation(LocationInfoRequest request) {
        if (request == null) {
            return null;
        }

        return LocationInfo.builder()
                .name(request.name())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
    }

    default List<StoryElement> toStoryElements(List<StoryElementRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        return requests.stream()
                .map(element -> StoryElement.builder()
                        .type(element.type())
                        .text(element.text())
                        .url(element.url())
                        .x(element.x())
                        .y(element.y())
                        .metadata(element.metadata())
                        .build())
                .toList();
    }
}
