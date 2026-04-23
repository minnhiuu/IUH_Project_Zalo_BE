package com.bondhub.socialfeedservice.repository;

import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.enums.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends MongoRepository<Post, String> {

    Optional<Post> findByIdAndActiveTrueAndIsCurrentTrue(String id);

    Page<Post> findByAuthorIdAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(String authorId, Pageable pageable);

    Page<Post> findByActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(Pageable pageable);

    Page<Post> findByPostTypeAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(PostType postType, Pageable pageable);

    Page<Post> findByPostTypeInAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(List<PostType> postTypes, Pageable pageable);

    // ── Internal / recommendation queries ─────────────────────────────────────

    /** Fetch posts by a set of author IDs — used by the recommendation service. */
    List<Post> findByAuthorIdInAndActiveTrueAndIsCurrentTrue(List<String> authorIds, Pageable pageable);

    /** Fetch posts by a set of author IDs filtered to a single PostType. */
    List<Post> findByAuthorIdInAndPostTypeAndActiveTrueAndIsCurrentTrue(List<String> authorIds, PostType postType, Pageable pageable);

    /**
     * Bulk-fetch active, current posts by their IDs.
     * Used by {@code PostServiceImpl#getPostsByIds} to hydrate recommendation results.
     */
    List<Post> findAllByIdInAndActiveTrueAndIsCurrentTrue(List<String> ids);
}

