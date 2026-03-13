package com.bondhub.common.repository;

import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.model.kafka.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {
    
    @Query("{ 'status': 'PENDING' }")
    List<OutboxEvent> findPendingEvents();
    
    @Query("{ 'status': 'FAILED', 'retryCount': { $lt: ?0 } }")
    List<OutboxEvent> findFailedEventsForRetry(int maxRetries);
    
    List<OutboxEvent> findByStatusAndRetryCountGreaterThanEqual(OutboxEvent.OutboxEventStatus status, int retryCount);
    
    Optional<OutboxEvent> findTopByAggregateIdAndEventTypeOrderByCreatedAtDesc(String aggregateId, EventType eventType);

    @Query("{ 'eventType': { $in: ?0 }, 'status': ?1 }")
    List<OutboxEvent> findByEventTypeInAndStatus(List<EventType> eventTypes, OutboxEvent.OutboxEventStatus status);

    @Query("{ 'eventType': { $in: ?0 }, 'status': ?1, 'updatedAt': { $lt: ?2 } }")
    List<OutboxEvent> findByEventTypeInAndStatusAndUpdatedAtBefore(List<EventType> eventTypes, OutboxEvent.OutboxEventStatus status, Instant beforeTime);

    long countByEventTypeInAndStatus(List<EventType> userIndexRequested, OutboxEvent.OutboxEventStatus outboxEventStatus);
}
