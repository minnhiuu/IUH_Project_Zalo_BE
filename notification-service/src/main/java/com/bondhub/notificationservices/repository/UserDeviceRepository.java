package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.enums.Platform;
import com.bondhub.notificationservices.model.UserDevice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends MongoRepository<UserDevice, String> {
    
    Optional<UserDevice> findByUserIdAndFcmToken(String userId, String fcmToken);

    List<UserDevice> findByUserId(String userId);

    void deleteByUserIdAndFcmToken(String userId, String fcmToken);

    void deleteByFcmTokenAndUserIdNot(String fcmToken, String userId);

    void deleteByUserIdAndPlatformIn(String userId, List<Platform> platforms);
}
