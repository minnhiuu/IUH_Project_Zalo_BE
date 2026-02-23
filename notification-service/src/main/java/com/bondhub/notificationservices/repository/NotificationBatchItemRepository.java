package com.bondhub.notificationservices.repository;

import com.bondhub.notificationservices.model.NotificationBatchItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationBatchItemRepository
        extends MongoRepository<NotificationBatchItem, String> {

    List<NotificationBatchItem> findByBatchKey(String batchKey);

    void deleteByBatchKey(String batchKey);
}