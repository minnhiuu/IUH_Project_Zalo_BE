package com.leafy.socialfeedservice.repository;

import com.leafy.socialfeedservice.model.Post;
import com.leafy.socialfeedservice.model.enums.PostType;
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
}
