package com.leafy.socialfeedservice.repository;

import com.leafy.socialfeedservice.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PostRepository extends MongoRepository<Post, String> {

    Optional<Post> findByIdAndActiveTrueAndIsCurrentTrue(String id);

    Page<Post> findByAuthorIdAndActiveTrueAndIsCurrentTrueOrderByUploadedAtDesc(String authorId, Pageable pageable);
}
