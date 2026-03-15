package com.bondhub.authservice.repository.redis;

import com.bondhub.authservice.enums.DeviceType;
import com.bondhub.authservice.model.redis.RefreshTokenSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenSessionRepository extends CrudRepository<RefreshTokenSession, String> {

    List<RefreshTokenSession> findByAccountId(String accountId);

    List<RefreshTokenSession> findByAccountIdAndDeviceType(String accountId, DeviceType deviceType);

    Optional<RefreshTokenSession> findByAccountIdAndDeviceId(String accountId, String deviceId);

    void deleteByAccountId(String accountId);

}
