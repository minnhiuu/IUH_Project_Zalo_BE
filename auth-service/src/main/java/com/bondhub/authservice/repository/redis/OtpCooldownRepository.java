package com.bondhub.authservice.repository.redis;

import com.bondhub.authservice.model.redis.OtpCooldown;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data Redis repository for OTP cooldown tracking
 */
@Repository
public interface OtpCooldownRepository extends CrudRepository<OtpCooldown, String> {
}
