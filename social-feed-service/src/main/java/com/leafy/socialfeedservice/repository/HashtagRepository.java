package com.leafy.socialfeedservice.repository;

import com.leafy.socialfeedservice.model.Hashtag;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HashtagRepository extends MongoRepository<Hashtag, String> {

    boolean existsByNormalizedValue(String normalizedValue);
}
