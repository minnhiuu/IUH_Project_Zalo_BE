package com.bondhub.socialfeedservice.service.comment;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.socialfeedservice.dto.request.comment.CreateCommentRequest;
import com.bondhub.socialfeedservice.dto.request.comment.UpdateCommentRequest;
import com.bondhub.socialfeedservice.dto.response.comment.CommentResponse;
import com.bondhub.socialfeedservice.mapper.CommentMapper;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.UserSummary;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import com.bondhub.socialfeedservice.publisher.CommentEventPublisher;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.ReactionRepository;
import com.bondhub.socialfeedservice.repository.UserSummaryRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    ReactionRepository reactionRepository;
    UserSummaryRepository userSummaryRepository;

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

        UserSummary author = userSummaryRepository.findById(currentUserId).orElse(null);
        return commentMapper.toCommentResponse(savedComment, null, author);
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
        UserSummary author = userSummaryRepository.findById(currentUserId).orElse(null);
        return commentMapper.toCommentResponse(updatedComment, null, author);
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
    public PageResponse<List<CommentResponse>> getRootCommentsByPost(String postId, int page, int size, String sortBy) {
        String currentUserId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);

        Page<Comment> comments = "MOST_REACTED".equalsIgnoreCase(sortBy)
                ? commentRepository.findByPostIdAndParentIdIsNullAndActiveTrueOrderByReactionCountDescCreatedAtDesc(postId, pageable)
                : commentRepository.findByPostIdAndParentIdIsNullAndActiveTrueOrderByCreatedAtAsc(postId, pageable);

        Map<String, UserSummary> authorById = loadAuthorSummaries(
                comments.stream().map(Comment::getAuthorId).collect(Collectors.toSet()));

        return PageResponse.fromPage(comments, comment ->
                commentMapper.toCommentResponse(
                        comment,
                        getCurrentUserReaction(currentUserId, comment.getId()),
                        authorById.get(comment.getAuthorId())));
    }

    @Override
    public List<CommentResponse> getRepliesByComment(String commentId) {
        String currentUserId = securityUtil.getCurrentUserId();
        getActiveComment(commentId);

        List<Comment> replies = commentRepository.findByParentIdAndActiveTrueOrderByCreatedAtAsc(commentId);

        Map<String, UserSummary> authorById = loadAuthorSummaries(
                replies.stream().map(Comment::getAuthorId).collect(Collectors.toSet()));

        return replies.stream()
                .map(comment -> commentMapper.toCommentResponse(
                        comment,
                        getCurrentUserReaction(currentUserId, comment.getId()),
                        authorById.get(comment.getAuthorId())))
                .toList();
    }

    private Map<String, UserSummary> loadAuthorSummaries(Set<String> authorIds) {
        return userSummaryRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(UserSummary::getId, u -> u));
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

    private ReactionType getCurrentUserReaction(String userId, String commentId) {
        return reactionRepository
                .findByAuthorIdAndTargetIdAndTargetType(userId, commentId, ReactionTargetType.COMMENT)
                .filter(r -> r.isActive())
                .map(r -> r.getType())
                .orElse(null);
    }

}
