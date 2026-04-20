package com.bondhub.socialfeedservice.repository;

import com.bondhub.socialfeedservice.model.UserWarning;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserWarningRepository extends MongoRepository<UserWarning, String> {

    List<UserWarning> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserId(String userId);
}
