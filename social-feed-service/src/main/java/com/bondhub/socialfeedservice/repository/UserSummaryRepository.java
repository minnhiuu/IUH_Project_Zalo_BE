package com.bondhub.socialfeedservice.repository;

import com.bondhub.socialfeedservice.model.UserSummary;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserSummaryRepository extends MongoRepository<UserSummary, String> {
}
