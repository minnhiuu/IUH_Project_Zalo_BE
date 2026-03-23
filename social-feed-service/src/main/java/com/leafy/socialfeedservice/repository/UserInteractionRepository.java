package com.leafy.socialfeedservice.repository;

import com.leafy.socialfeedservice.model.UserInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {

    Page<UserInteraction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<UserInteraction> findByPostIdOrderByCreatedAtDesc(String postId, Pageable pageable);

    List<UserInteraction> findTopByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}