package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.model.UserNotificationState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationStateRepository extends MongoRepository<UserNotificationState, String> {
}
