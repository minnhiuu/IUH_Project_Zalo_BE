package com.bondhub.socialfeedservice.mapper;

import com.bondhub.socialfeedservice.dto.request.comment.CreateCommentRequest;
import com.bondhub.socialfeedservice.dto.request.comment.UpdateCommentRequest;
import com.bondhub.socialfeedservice.dto.request.post.PostMediaRequest;
import com.bondhub.socialfeedservice.dto.response.comment.CommentResponse;
import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import org.mapstruct.Mapper;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    default Comment toComment(CreateCommentRequest request) {
        return Comment.builder()
                .postId(request.postId())
                .parentId(request.parentId())
                .content(request.content())
                .media(toMedia(request.media()))
                .build();
    }

    default CommentResponse toCommentResponse(Comment comment) {
        return toCommentResponse(comment, null, null, "");
    }

    default CommentResponse toCommentResponse(Comment comment, ReactionType currentUserReaction) {
        return toCommentResponse(comment, currentUserReaction, null, "");
    }

    default CommentResponse toCommentResponse(Comment comment, ReactionType currentUserReaction, UserSummary author, String s3BaseUrl) {
        AuthorInfo authorInfo = AuthorInfo.builder()
                .id(comment.getAuthorId())
                .fullName(author != null ? author.getFullName() : null)
                .avatar(author != null && author.getAvatar() != null ? resolveMediaUrl(author.getAvatar(), s3BaseUrl) : null)
                .build();

        List<PostMedia> resolvedMedia = comment.getMedia() == null ? null
                : comment.getMedia().stream()
                        .map(m -> PostMedia.builder()
                                .url(resolveMediaUrl(m.getUrl(), s3BaseUrl))
                                .type(m.getType())
                                .build())
                        .toList();

        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorInfo(authorInfo)
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .media(resolvedMedia)
                .replyDepth(comment.getReplyDepth())
                .replyCount(comment.getReplyCount())
                .reactionCount(comment.getReactionCount())
                .currentUserReaction(currentUserReaction)
                .topReactions(comment.getTopReactions())
                .isEdited(comment.isEdited())
                .createdAt(comment.getCreatedAt())
                .lastModifiedAt(comment.getLastModifiedAt())
                .build();
    }

    default String resolveMediaUrl(String url, String s3BaseUrl) {
        if (url == null || url.isBlank() || s3BaseUrl == null) {
            return url;
        }

        // Clean up legacy localhost URLs if present
        if (url.contains("/api/files/download/")) {
            String key = url.substring(url.lastIndexOf("/api/files/download/") + "/api/files/download/".length());
            key = key.replace("%2F", "/").replace("%2f", "/");
            return s3BaseUrl + key;
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return s3BaseUrl + url;
    }

    default void updateComment(Comment comment, UpdateCommentRequest request) {
        comment.setContent(request.content());
        if (request.media() != null) {
            comment.setMedia(toMedia(request.media()));
        }
        comment.setEdited(true);
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
