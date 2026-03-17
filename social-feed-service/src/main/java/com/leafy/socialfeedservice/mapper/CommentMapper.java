package com.leafy.socialfeedservice.mapper;

import com.leafy.socialfeedservice.dto.request.comment.CreateCommentRequest;
import com.leafy.socialfeedservice.dto.request.comment.UpdateCommentRequest;
import com.leafy.socialfeedservice.dto.request.post.PostMediaRequest;
import com.leafy.socialfeedservice.dto.response.comment.CommentResponse;
import com.leafy.socialfeedservice.model.Comment;
import com.leafy.socialfeedservice.model.embedded.PostMedia;
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
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .media(comment.getMedia())
                .replyDepth(comment.getReplyDepth())
                .replyCount(comment.getReplyCount())
                .reactionCount(comment.getReactionCount())
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
