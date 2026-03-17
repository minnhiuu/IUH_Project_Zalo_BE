package com.leafy.socialfeedservice.service.post;

import com.bondhub.common.dto.PageResponse;
import com.leafy.socialfeedservice.dto.request.post.CreatePostRequest;
import com.leafy.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.leafy.socialfeedservice.dto.response.post.PostResponse;

import java.util.List;

public interface PostService {

    PostResponse createPost(CreatePostRequest request);

    PostResponse getPostById(String postId);

    PageResponse<List<PostResponse>> getMyPosts(int page, int size);

    PostResponse updatePost(String postId, UpdatePostRequest request);

    void deletePost(String postId);
}
