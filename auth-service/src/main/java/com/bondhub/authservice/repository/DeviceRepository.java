package com.bondhub.authservice.repository;

import com.bondhub.authservice.model.Device;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends MongoRepository<Device, String> {

    Optional<Device> findBySessionId(String sessionId);

    Optional<Device> findByDeviceIdAndAccountId(String deviceId, String accountId);

    List<Device> findByAccountId(String accountId);

    boolean existsBySessionId(String sessionId);

    void deleteByAccountId(String accountId);
}
