package com.bondhub.socialfeedservice.service.comment;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.request.comment.CreateCommentRequest;
import com.bondhub.socialfeedservice.dto.request.comment.UpdateCommentRequest;
import com.bondhub.socialfeedservice.dto.response.comment.CommentResponse;

import java.util.List;

public interface CommentService {

    CommentResponse createComment(CreateCommentRequest request);

    CommentResponse updateComment(String commentId, UpdateCommentRequest request);

    void deleteComment(String commentId);

    PageResponse<List<CommentResponse>> getRootCommentsByPost(String postId, int page, int size, String sortBy);

    List<CommentResponse> getRepliesByComment(String commentId);
}
