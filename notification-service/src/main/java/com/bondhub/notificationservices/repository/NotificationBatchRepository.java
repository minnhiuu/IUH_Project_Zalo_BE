package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.enums.BatchStatus;
import com.bondhub.notificationservices.model.NotificationBatch;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationBatchRepository extends MongoRepository<NotificationBatch, String> {

    Optional<NotificationBatch> findByBatchKey(String batchKey);

    List<NotificationBatch> findByStatusAndWindowExpiresAtBefore(
            BatchStatus status,
            LocalDateTime cutoff
    );
}
