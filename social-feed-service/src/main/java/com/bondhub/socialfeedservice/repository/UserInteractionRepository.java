package com.bondhub.socialfeedservice.repository;

import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.socialfeedservice.model.UserInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {

    Page<UserInteraction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<UserInteraction> findByPostIdOrderByCreatedAtDesc(String postId, Pageable pageable);

    List<UserInteraction> findTopByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<UserInteraction> findByUserIdAndPostIdAndInteractionType(
            String userId, String postId, InteractionType interactionType);

    boolean existsByUserIdAndPostIdAndInteractionType(
            String userId, String postId, InteractionType interactionType);
}
