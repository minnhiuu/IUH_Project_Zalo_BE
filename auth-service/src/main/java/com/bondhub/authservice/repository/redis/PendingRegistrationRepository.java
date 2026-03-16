package com.bondhub.authservice.repository.redis;

import com.bondhub.authservice.model.redis.PendingRegistration;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for pending registration data
 */
@Repository
public interface PendingRegistrationRepository extends CrudRepository<PendingRegistration, String> {
}
