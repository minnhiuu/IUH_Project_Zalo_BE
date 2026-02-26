package com.bondhub.common.repository;

import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.model.kafka.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {
    Optional<OutboxEvent> findTopByAggregateIdAndEventTypeOrderByCreatedAtDesc(String aggregateId, EventType eventType);

    long deleteByStatusAndCreatedAtBefore(OutboxEvent.OutboxEventStatus outboxEventStatus, LocalDateTime cutoff);

    long countByStatusAndCreatedAtBefore(OutboxEvent.OutboxEventStatus outboxEventStatus, LocalDateTime stuckThreshold);

    List<OutboxEvent> findByStatusAndCreatedAtBefore(OutboxEvent.OutboxEventStatus status, LocalDateTime threshold);
}
