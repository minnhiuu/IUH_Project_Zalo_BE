package com.bondhub.authservice.repository.redis;

import com.bondhub.authservice.model.redis.QrSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrSessionRepository extends CrudRepository<QrSession, String> {
}
