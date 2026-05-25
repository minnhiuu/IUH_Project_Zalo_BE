package com.bondhub.searchservice.repository.mongodb;

import com.bondhub.searchservice.model.mongodb.UserInteractionFeature;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserInteractionFeatureRepository extends MongoRepository<UserInteractionFeature, String> {

    List<UserInteractionFeature> findByUserIdAndTargetUserIdIn(String userId, Collection<String> targetUserIds);

    Optional<UserInteractionFeature> findByUserIdAndTargetUserId(String userId, String targetUserId);
}
