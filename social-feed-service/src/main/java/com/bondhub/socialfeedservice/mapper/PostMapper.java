package com.bondhub.socialfeedservice.mapper;

import com.bondhub.socialfeedservice.dto.request.post.CreatePostRequest;
import com.bondhub.socialfeedservice.dto.request.post.LocationInfoRequest;
import com.bondhub.socialfeedservice.dto.request.post.PostContentRequest;
import com.bondhub.socialfeedservice.dto.request.post.PostMediaRequest;
import com.bondhub.socialfeedservice.dto.request.post.PostMusicRequest;
import com.bondhub.socialfeedservice.dto.request.post.StoryElementRequest;
import com.bondhub.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.dto.response.post.PostResponse;
import com.bondhub.socialfeedservice.dto.response.post.SharedPostPreview;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.model.embedded.LocationInfo;
import com.bondhub.socialfeedservice.model.embedded.PostContent;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import com.bondhub.socialfeedservice.model.embedded.PostMusic;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
import com.bondhub.socialfeedservice.model.embedded.StoryElement;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import org.mapstruct.Mapper;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PostMapper {

    default PostResponse toPostResponse(Post post, String s3BaseUrl, ReactionType currentUserReaction, UserSummary author) {
        return toPostResponse(post, s3BaseUrl, currentUserReaction, author, null);
    }

    default PostResponse toPostResponse(Post post, String s3BaseUrl, ReactionType currentUserReaction, UserSummary author, SharedPostPreview sharedPostPreview) {
        List<PostMedia> resolvedMedia = post.getMedia() == null ? new ArrayList<>() :
                post.getMedia().stream()
                        .map(m -> PostMedia.builder()
                                .url(resolveMediaUrl(m.getUrl(), s3BaseUrl))
                                .type(m.getType())
                                .build())
                        .toList();

        AuthorInfo authorInfo = AuthorInfo.builder()
                .id(post.getAuthorId())
                .fullName(author != null ? author.getFullName() : null)
                .avatar(author != null && author.getAvatar() != null
                        ? resolveMediaUrl(author.getAvatar(), s3BaseUrl)
                        : null)
                .build();

        return PostResponse.builder()
                .id(post.getId())
                .authorInfo(authorInfo)
                .groupId(post.getGroupId())
                .content(post.getContent())
                .media(resolvedMedia)
                .postType(post.getPostType())
                .sharedPostId(post.getSharedPostId())
                .sharedPostPreview(sharedPostPreview)
                .originalAuthorId(post.getOriginalAuthorId())
                .sharedCaption(post.getSharedCaption())
                .rootPostId(post.getRootPostId())
                .expiresAt(post.getExpiresAt())
                .music(post.getMusic())
                .viewerIds(post.getViewerIds())
                .location(post.getLocation())
                .elements(post.getElements())
                .visibility(post.getVisibility())
                .stats(post.getStats())
                .currentUserReaction(currentUserReaction)
                .uploadedAt(post.getUploadedAt())
                .updatedAt(post.getUpdatedAt())
                .version(post.getVersion())
                .isCurrent(post.isCurrent())
                .isEdited(post.isEdited())
                .build();
    }


    default String resolveMediaUrl(String url, String s3BaseUrl) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return s3BaseUrl + url;
    }

    default Post toPost(CreatePostRequest request) {
        return Post.builder()
                .groupId(request.groupId())
                .content(PostContent.builder()
                        .caption(request.caption())
                        .hashtags(request.hashtags())
                        .build())
                .media(toMedia(request.media()))
                .postType(request.postType())
            .sharedPostId(request.sharedPostId())
            .sharedCaption(toPostContent(request.sharedCaption()))
            .expiresAt(request.expiresAt())
            .music(toPostMusic(request.music()))
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
                .caption(request.caption() != null ? request.caption() : currentContent.getCaption())
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

        if (request.music() != null) {
            post.setMusic(toPostMusic(request.music()));
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
                .caption(request.caption())

                .hashtags(request.hashtags())
                .build();
    }

    default PostContent mergePostContent(PostContent currentContent, PostContentRequest request) {
        return PostContent.builder()
                .caption(request.caption() != null ? request.caption() : currentContent.getCaption())
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

    default PostMusic toPostMusic(PostMusicRequest request) {
        if (request == null) {
            return null;
        }
        return PostMusic.builder()
                .jamendoId(request.jamendoId())
                .title(request.title())
                .artistName(request.artistName())
                .audioUrl(request.audioUrl())
                .coverUrl(request.coverUrl())
                .duration(request.duration())
                .albumName(request.albumName())
                .build();
    }
}
