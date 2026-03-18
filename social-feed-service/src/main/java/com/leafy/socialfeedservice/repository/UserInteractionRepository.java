package com.leafy.socialfeedservice.repository;

import com.leafy.socialfeedservice.model.UserInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {
}