package com.leafy.socialfeedservice.repository;

import com.leafy.socialfeedservice.model.Reaction;
import com.leafy.socialfeedservice.model.enums.ReactionTargetType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ReactionRepository extends MongoRepository<Reaction, String> {

    Optional<Reaction> findByAuthorIdAndTargetIdAndTargetType(String authorId, String targetId, ReactionTargetType targetType);

    long countByTargetIdAndTargetTypeAndActiveTrue(String targetId, ReactionTargetType targetType);
}
