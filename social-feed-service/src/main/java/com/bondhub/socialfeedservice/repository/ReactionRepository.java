package com.bondhub.socialfeedservice.repository;

import com.bondhub.socialfeedservice.model.Reaction;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends MongoRepository<Reaction, String> {

    Optional<Reaction> findByAuthorIdAndTargetIdAndTargetType(String authorId, String targetId, ReactionTargetType targetType);

    List<Reaction> findByTargetIdAndTargetTypeAndActiveTrueOrderByCreatedAtDesc(String targetId, ReactionTargetType targetType);

    List<Reaction> findByTargetTypeAndTypeAndActiveTrueOrderByCreatedAtDesc(ReactionTargetType targetType, ReactionType type);

    long countByTargetIdAndTargetTypeAndActiveTrue(String targetId, ReactionTargetType targetType);
}
