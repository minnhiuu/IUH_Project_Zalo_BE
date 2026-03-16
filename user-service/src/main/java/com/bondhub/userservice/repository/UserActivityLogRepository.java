package com.bondhub.userservice.repository;

import com.bondhub.userservice.model.UserActivityLog;
import com.bondhub.userservice.model.enums.UserAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityLogRepository extends MongoRepository<UserActivityLog, String> {
    
    /**
     * Find all activity logs for a specific user with pagination
     */
    Page<UserActivityLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find activity logs by user and action type
     */
    List<UserActivityLog> findByUserIdAndAction(String userId, UserAction action);
    
    /**
     * Find activity logs within a date range
     */
    Page<UserActivityLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        String userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find the most recent log of a specific action for a user
     */
    java.util.Optional<UserActivityLog> findTopByUserIdAndActionOrderByCreatedAtDesc(String userId, UserAction action);

    /**
     * Count total activity logs for a user
     */
    long countByUserId(String userId);
}
