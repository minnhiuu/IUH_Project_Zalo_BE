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

    PageResponse<List<PostResponse>> getPostsByUserId(String userId, int page, int size);

    PageResponse<List<PostResponse>> getFeedAndSharePosts(int page, int size);

    List<StoryGroupResponse> getStoryPosts(int page, int size);

    PageResponse<List<PostResponse>> getReelPosts(int page, int size);

    PostResponse updatePost(String postId, UpdatePostRequest request);

//    PageResponse<List<PostResponse>> getUserPosts(String userId, int page, int size);

    void deletePost(String postId);

    /**
     * Fetch the most-recent posts authored by the supplied user IDs.
     *
     * <p>Used internally by the recommendation service to assemble the
     * {@code friend_posts} and {@code peer_posts} candidate streams.
     *
     * @param authorIds List of author user IDs to query.
     * @param postType  Optional PostType name filter (e.g. {@code "FEED"}, {@code "REEL"}).
     *                  Pass {@code null} to return all active post types.
     * @param limit     Maximum total posts to return (capped by caller before this method).
     * @return Flat list of matching {@link PostResponse} objects, ordered most-recent first.
     */
    List<PostResponse> getPostsByAuthors(List<String> authorIds, String postType, int limit);

    /**
     * Hydrate an ordered list of post IDs into full {@link PostResponse} objects.
     *
     * <p>The returned list preserves the order of {@code postIds} so that
     * recommendation ranking is not disturbed.  Post IDs that cannot be found
     * (e.g. soft-deleted) are silently skipped.
     *
     * @param postIds       Ordered list of post IDs to fetch (recommendation rank order).
     * @param currentUserId ID of the requesting user (used to resolve reaction state).
     * @return Ordered list of {@link PostResponse} objects.
     */
    List<PostResponse> getPostsByIds(List<String> postIds, String currentUserId);
}

