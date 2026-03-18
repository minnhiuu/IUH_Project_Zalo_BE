package com.leafy.socialfeedservice.service.comment;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.leafy.socialfeedservice.dto.request.comment.CreateCommentRequest;
import com.leafy.socialfeedservice.dto.request.comment.UpdateCommentRequest;
import com.leafy.socialfeedservice.dto.response.comment.CommentResponse;
import com.leafy.socialfeedservice.mapper.CommentMapper;
import com.leafy.socialfeedservice.model.Comment;
import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.publisher.CommentEventPublisher;
import com.leafy.socialfeedservice.repository.CommentRepository;
import com.leafy.socialfeedservice.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentServiceImpl implements CommentService {

    CommentRepository commentRepository;
    PostRepository postRepository;
    CommentMapper commentMapper;
    SecurityUtil securityUtil;
    CommentEventPublisher commentEventPublisher;

    @Override
    @Transactional
    public CommentResponse createComment(CreateCommentRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        Post post = getActivePost(request.postId());

        Comment comment = commentMapper.toComment(request);
        comment.setAuthorId(currentUserId);

        if (request.parentId() != null && !request.parentId().isBlank()) {
            Comment parentComment = getActiveComment(request.parentId());
            if (!parentComment.getPostId().equals(request.postId())) {
                throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
            }
            comment.setReplyDepth(parentComment.getReplyDepth() + 1);
            parentComment.setReplyCount(parentComment.getReplyCount() + 1);
            commentRepository.save(parentComment);
        }

        Comment savedComment = commentRepository.save(comment);

        commentEventPublisher.publishPostCommentCountProjectionRequested(
            currentUserId,
            post.getId(),
            savedComment.getId(),
            "INCREMENT");

        commentEventPublisher.publishCommentInteraction(
                currentUserId,
                post.getId(),
                post.getGroupId());

        return commentMapper.toCommentResponse(savedComment);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(String commentId, UpdateCommentRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        Comment comment = getActiveComment(commentId);

        validateOwner(comment.getAuthorId(), currentUserId);

        commentMapper.updateComment(comment, request);
        comment.setLastModifiedAt(LocalDateTime.now());

        Comment updatedComment = commentRepository.save(comment);
        return commentMapper.toCommentResponse(updatedComment);
    }

    @Override
    @Transactional
    public void deleteComment(String commentId) {
        String currentUserId = securityUtil.getCurrentUserId();
        Comment comment = getActiveComment(commentId);

        validateOwner(comment.getAuthorId(), currentUserId);

        comment.setActive(false);
        comment.setLastModifiedAt(LocalDateTime.now());
        commentRepository.save(comment);

        Post post = getActivePost(comment.getPostId());
        commentEventPublisher.publishPostCommentCountProjectionRequested(
            currentUserId,
            post.getId(),
            comment.getId(),
            "DECREMENT");
    }

    @Override
    public PageResponse<List<CommentResponse>> getRootCommentsByPost(String postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Comment> comments = commentRepository.findByPostIdAndParentIdIsNullAndActiveTrueOrderByCreatedAtAsc(postId, pageable);
        return PageResponse.fromPage(comments, commentMapper::toCommentResponse);
    }

    private Post getActivePost(String postId) {
        return postRepository.findByIdAndActiveTrueAndIsCurrentTrue(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
    }

    private Comment getActiveComment(String commentId) {
        return commentRepository.findByIdAndActiveTrue(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private void validateOwner(String ownerId, String currentUserId) {
        if (!ownerId.equals(currentUserId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

}
