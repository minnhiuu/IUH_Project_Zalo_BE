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
        return toCommentResponse(comment, null, null);
    }

    default CommentResponse toCommentResponse(Comment comment, ReactionType currentUserReaction) {
        return toCommentResponse(comment, currentUserReaction, null);
    }

    default CommentResponse toCommentResponse(Comment comment, ReactionType currentUserReaction, UserSummary author) {
        AuthorInfo authorInfo = AuthorInfo.builder()
                .id(comment.getAuthorId())
                .fullName(author != null ? author.getFullName() : null)
                .avatar(author != null ? author.getAvatar() : null)
                .build();

        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorInfo(authorInfo)
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .media(comment.getMedia())
                .replyDepth(comment.getReplyDepth())
                .replyCount(comment.getReplyCount())
                .reactionCount(comment.getReactionCount())
                .currentUserReaction(currentUserReaction)
                .isEdited(comment.isEdited())
                .createdAt(comment.getCreatedAt())
                .lastModifiedAt(comment.getLastModifiedAt())
                .build();
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
