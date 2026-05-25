package com.bondhub.socialfeedservice.repository;

import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.enums.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import com.bondhub.socialfeedservice.model.enums.Visibility;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends MongoRepository<Post, String> {

    Optional<Post> findByIdAndActiveTrueAndIsCurrentTrue(String id);

    Optional<Post> findByIdAndActiveTrueAndIsCurrentTrueAndHiddenFalse(String id);

    Page<Post> findByAuthorIdAndActiveTrueAndIsCurrentTrueAndHiddenFalseOrderByUploadedAtDesc(String authorId, Pageable pageable);

    Page<Post> findByActiveTrueAndIsCurrentTrueAndHiddenFalseOrderByUploadedAtDesc(Pageable pageable);

    Page<Post> findByPostTypeAndActiveTrueAndIsCurrentTrueAndHiddenFalseOrderByUploadedAtDesc(PostType postType, Pageable pageable);

    Page<Post> findByPostTypeInAndActiveTrueAndIsCurrentTrueAndHiddenFalseOrderByUploadedAtDesc(List<PostType> postTypes, Pageable pageable);

    Page<Post> findByPostTypeInAndActiveTrueAndIsCurrentTrueAndHiddenFalseAndIdNotInOrderByUploadedAtDesc(List<PostType> postTypes, List<String> excludedIds, Pageable pageable);

    Page<Post> findByAuthorIdAndVisibilityInAndActiveTrueAndIsCurrentTrueAndHiddenFalseOrderByUploadedAtDesc(String authorId, List<Visibility> visibilities, Pageable pageable);

    List<Post> findByAuthorIdAndPostTypeAndVisibilityInAndActiveTrueAndIsCurrentTrueAndHiddenFalseOrderByUploadedAtDesc(String authorId, PostType postType, List<Visibility> visibilities, Pageable pageable);

    @Query(value = "{ 'postType': { $in: ?0 }, 'active': true, 'isCurrent': true, 'hidden': false, '$or': [ { 'visibility': { $in: ['ALL', null] } }, { 'visibility': 'FRIEND', 'authorId': { $in: ?1 } }, { 'visibility': 'ONLY_ME', 'authorId': ?2 } ] }", sort = "{ 'uploadedAt': -1 }")
    Page<Post> findVisibleFeedPosts(List<PostType> postTypes, List<String> friendIdsAndMe, String currentUserId, Pageable pageable);

    @Query(value = "{ 'postType': { $in: ?0 }, 'active': true, 'isCurrent': true, 'hidden': false, '_id': { $nin: ?1 }, '$or': [ { 'visibility': { $in: ['ALL', null] } }, { 'visibility': 'FRIEND', 'authorId': { $in: ?2 } }, { 'visibility': 'ONLY_ME', 'authorId': ?3 } ] }", sort = "{ 'uploadedAt': -1 }")
    Page<Post> findVisibleFeedPostsExcluding(List<PostType> postTypes, List<String> excludedIds, List<String> friendIdsAndMe, String currentUserId, Pageable pageable);

    @Query(value = "{ 'postType': ?0, 'active': true, 'isCurrent': true, 'hidden': false, '$or': [ { 'visibility': { $in: ['ALL', null] } }, { 'visibility': 'FRIEND', 'authorId': { $in: ?1 } }, { 'visibility': 'ONLY_ME', 'authorId': ?2 } ] }", sort = "{ 'uploadedAt': -1 }")
    Page<Post> findVisiblePostsByType(PostType postType, List<String> friendIdsAndMe, String currentUserId, Pageable pageable);

    @Query(value = "{ 'postType': ?0, 'authorId': { $in: ?1 }, 'active': true, 'isCurrent': true, 'hidden': false, '$or': [ { 'visibility': { $in: ['ALL', 'FRIEND', null] } }, { 'visibility': 'ONLY_ME', 'authorId': ?2 } ] }", sort = "{ 'uploadedAt': -1 }")
    Page<Post> findVisiblePostsFromFriendsAndMe(PostType postType, List<String> friendIdsAndMe, String currentUserId, Pageable pageable);

    // ── Internal / recommendation queries ─────────────────────────────────────

    /** Fetch posts by a set of author IDs — used by the recommendation service. */
    List<Post> findByAuthorIdInAndActiveTrueAndIsCurrentTrueAndHiddenFalse(List<String> authorIds, Pageable pageable);

    /** Fetch posts by a set of author IDs filtered to a single PostType. */
    List<Post> findByAuthorIdInAndPostTypeAndActiveTrueAndIsCurrentTrueAndHiddenFalse(List<String> authorIds, PostType postType, Pageable pageable);

    /**
     * Bulk-fetch active, current posts by their IDs.
     * Used by {@code PostServiceImpl#getPostsByIds} to hydrate recommendation results.
     */
    List<Post> findAllByIdInAndActiveTrueAndIsCurrentTrueAndHiddenFalse(List<String> ids);
}

