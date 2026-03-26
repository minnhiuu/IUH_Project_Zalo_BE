package com.bondhub.socialfeedservice.repository;

import com.bondhub.socialfeedservice.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends MongoRepository<Comment, String> {

    Optional<Comment> findByIdAndActiveTrue(String id);

    Page<Comment> findByPostIdAndParentIdIsNullAndActiveTrueOrderByCreatedAtAsc(String postId, Pageable pageable);

    Page<Comment> findByPostIdAndParentIdIsNullAndActiveTrueOrderByReactionCountDescCreatedAtDesc(String postId, Pageable pageable);

    List<Comment> findByParentIdAndActiveTrueOrderByCreatedAtAsc(String parentId);

    long countByPostIdAndActiveTrue(String postId);
}
