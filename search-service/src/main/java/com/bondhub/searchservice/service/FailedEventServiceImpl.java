package com.bondhub.searchservice.service;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.searchservice.dto.response.FailedEventResponse;
import com.bondhub.searchservice.mapper.FailedEventMapper;
import com.bondhub.searchservice.model.mongodb.FailedEvent;
import com.bondhub.searchservice.repository.FailedEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedEventServiceImpl implements FailedEventService {
 
    private final FailedEventRepository repository;
    private final FailedEventMapper mapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MongoTemplate mongoTemplate;

    @Override
    public void logFailure(String eventId, EventType eventType, String topic, Integer partition, Long offset, String payload, String errorMessage, String stackTrace, int retryCount) {
        log.error("Logging failed event: {} from topic: {}, partition: {}, offset: {}, eventId: {}, error: {}", 
                eventType, topic, partition, offset, eventId, errorMessage);
        
        FailedEvent event = FailedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .topic(topic)
                .partition(partition)
                .offset(offset)
                .payload(payload)
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .retryCount(retryCount)
                .resolved(false)
                .build();
        
        repository.save(event);
    }

    @Override
    public PageResponse<List<FailedEventResponse>> getEventsByResolved(Boolean resolved, String keyword, Integer hours, Pageable pageable) {
        Query query = new Query().with(pageable);
        List<Criteria> criteriaList = new ArrayList<>();

        if (resolved != null) {
            criteriaList.add(Criteria.where("resolved").is(resolved));
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            String k = keyword.trim();
            criteriaList.add(new Criteria().orOperator(
                Criteria.where("eventId").regex(k, "i"),
                Criteria.where("errorMessage").regex(k, "i")
            ));
        }

        if (hours != null && hours > 0) {
            criteriaList.add(Criteria.where("createdAt").gte(LocalDateTime.now().minusHours(hours)));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        List<FailedEvent> events = mongoTemplate.find(query, FailedEvent.class);
        Page<FailedEvent> entitiesPage = PageableExecutionUtils.getPage(
            events, 
            pageable, 
            () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), FailedEvent.class)
        );

        return PageResponse.fromPage(entitiesPage, mapper::toDto);
    }
    
    @Override
    public long countEventsByResolved(boolean resolved) {
        return repository.countByResolved(resolved);
    }

    @Override
    public void updateResolved(String id, boolean resolved) {
        repository.findById(id).ifPresent(event -> {
            event.setResolved(resolved);
            repository.save(event);
        });
    }

    @Override
    public FailedEventResponse getEventById(String id) {
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new AppException(ErrorCode.DLQ_EVENT_NOT_FOUND));
    }

    @Override
    public void retryEvent(String id) {
        repository.findById(id).ifPresent(event -> {
            if (!event.isResolved()) {
                this.retry(event);
            }
        });
    }

    @Override
    public void retryEvents(List<String> ids) {
        List<FailedEvent> events = repository.findAllById(ids);
        events.stream()
                .filter(event -> !event.isResolved())
                .forEach(this::retry);
    }

    @Override
    public void retryAllEvents() {
        List<FailedEvent> unresolvedEvents = repository.findAllByResolved(false);
        unresolvedEvents.forEach(this::retry);
    }

    @Override
    public void retryEventsByDuration(int hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        List<FailedEvent> events = repository.findAllByResolvedAndCreatedAtAfter(false, startTime);
        events.forEach(this::retry);
    }

    private void retry(FailedEvent event) {
        log.info("Retrying event: {} to topic: {}", event.getId(), event.getTopic());
        try {
            kafkaTemplate.send(event.getTopic(), event.getPayload());
            event.setResolved(true);
            event.setRetryCount(event.getRetryCount() + 1);
            repository.save(event);
        } catch (Exception e) {
            log.error("Failed to retry event: {}", event.getId(), e);
            event.setRetryCount(event.getRetryCount() + 1);
            repository.save(event);
        }
    }
}
