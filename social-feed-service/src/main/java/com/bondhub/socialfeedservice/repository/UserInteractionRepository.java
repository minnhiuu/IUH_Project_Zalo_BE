package com.bondhub.socialfeedservice.repository;

import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.socialfeedservice.model.UserInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {

    Page<UserInteraction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<UserInteraction> findByPostIdOrderByCreatedAtDesc(String postId, Pageable pageable);

    Page<UserInteraction> findByPostIdAndInteractionTypeOrderByCreatedAtDesc(String postId, InteractionType interactionType, Pageable pageable);

    List<UserInteraction> findTopByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<UserInteraction> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant createdAt, Pageable pageable);

    Optional<UserInteraction> findByUserIdAndPostIdAndInteractionType(
            String userId, String postId, InteractionType interactionType);

    List<UserInteraction> findByUserIdAndInteractionType(String userId, InteractionType interactionType);

    boolean existsByUserIdAndPostIdAndInteractionType(
            String userId, String postId, InteractionType interactionType);

    List<UserInteraction> findByUserIdAndPostIdInAndInteractionType(
            String userId, List<String> postIds, InteractionType interactionType);
}
