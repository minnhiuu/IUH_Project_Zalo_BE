package com.bondhub.socialfeedservice.service.post;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.request.post.CreatePostRequest;
import com.bondhub.socialfeedservice.dto.request.post.UpdatePostRequest;
import com.bondhub.socialfeedservice.dto.response.post.PostResponse;
import com.bondhub.socialfeedservice.dto.response.post.StoryGroupResponse;

import java.util.List;

public interface PostService {

    PostResponse createPost(CreatePostRequest request);

    PostResponse getPostById(String postId);

    PageResponse<List<PostResponse>> getMyPosts(int page, int size);

    PageResponse<List<PostResponse>> getFeedAndSharePosts(int page, int size);

    List<StoryGroupResponse> getStoryPosts(int page, int size);

    PageResponse<List<PostResponse>> getReelPosts(int page, int size);

    PostResponse updatePost(String postId, UpdatePostRequest request);

    void deletePost(String postId);
}
