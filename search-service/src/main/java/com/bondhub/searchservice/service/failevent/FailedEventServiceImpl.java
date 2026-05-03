package com.bondhub.searchservice.service.failevent;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.searchservice.dto.request.FailedEventFilter;
import com.bondhub.searchservice.dto.response.FailedEventResponse;
import com.bondhub.searchservice.mapper.FailedEventMapper;
import com.bondhub.searchservice.model.mongodb.FailedEvent;
import com.bondhub.searchservice.repository.mongodb.FailedEventRepository;
import com.bondhub.common.event.message.MessageIndexRequestedEvent;
import com.bondhub.common.event.user.UserIndexDeletedEvent;
import com.bondhub.common.event.user.UserIndexRequestedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedEventServiceImpl implements FailedEventService {
 
    private final FailedEventRepository repository;
    private final FailedEventMapper mapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void logFailure(String eventId, EventType eventType, String topic, Integer partition, Long offset, String payload, String errorMessage, String stackTrace, int retryCount) {
        log.error("Logging failed event: {} from topic: {}, partition: {}, offset: {}, eventId: {}, error: {}", 
                eventType, topic, partition, offset, eventId, errorMessage);

        // Try to find an existing unresolved record for this specific event
        FailedEvent existingEvent = repository.findFirstByEventIdAndEventTypeAndResolvedFalseOrderByCreatedAtDesc(eventId, eventType)
                .orElseGet(() -> {
                    // If not found, try to find the most recent resolved one to "reopen" it
                    return repository.findFirstByEventIdAndEventTypeOrderByCreatedAtDesc(eventId, eventType)
                            .orElse(null);
                });

        if (existingEvent != null) {
            log.info("Updating existing failed event record: ID={}, eventId={}, previousRetry={}", 
                    existingEvent.getId(), eventId, existingEvent.getRetryCount());
            
            existingEvent.setTopic(topic);
            existingEvent.setPartition(partition);
            existingEvent.setOffset(offset);
            existingEvent.setErrorMessage(errorMessage);
            existingEvent.setStackTrace(stackTrace);
            existingEvent.setResolved(false);
            
            // If the incoming retryCount is 0 (default from listener), we use our internal count + 1
            // because we know this is a new failure instance
            if (retryCount <= 0) {
                existingEvent.setRetryCount(existingEvent.getRetryCount() + 1);
            } else {
                existingEvent.setRetryCount(retryCount);
            }
            
            repository.save(existingEvent);
        } else {
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
    }

    @Override
    public PageResponse<List<FailedEventResponse>> getEventsPaged(FailedEventFilter filter) {
        Pageable pageable = PageRequest.of(
            filter.getPage(), 
            filter.getSize(), 
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Query query = new Query().with(pageable);
        List<Criteria> criteriaList = new ArrayList<>();

        if (filter.resolved() != null) {
            criteriaList.add(Criteria.where("resolved").is(filter.resolved()));
        }

        if (filter.keyword() != null && !filter.keyword().trim().isEmpty()) {
            String k = filter.keyword().trim();
            criteriaList.add(new Criteria().orOperator(
                Criteria.where("eventId").regex(k, "i"),
                Criteria.where("errorMessage").regex(k, "i")
            ));
        }

        if (filter.hours() != null && filter.hours() > 0) {
            criteriaList.add(Criteria.where("createdAt").gte(LocalDateTime.now().minusHours(filter.hours())));
        }

        if (filter.type() != null) {
            Collection<String> topics = filter.type().getTopics();
            if (topics != null && !topics.isEmpty()) {
                Set<String> allTopics = new HashSet<>(topics);
                topics.forEach(t -> allTopics.add(t + ".dlq"));
                criteriaList.add(Criteria.where("topic").in(allTopics));
            }
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
    public long countEventsByResolvedAndTopics(boolean resolved, Collection<String> topics) {
        return repository.countByResolvedAndTopicIn(resolved, topics);
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
    public void retryAllEvents(Collection<String> topics) {
        List<FailedEvent> events;
        if (topics == null || topics.isEmpty()) {
            events = repository.findAllByResolved(false);
            log.info("Retrying all unresolved events (count={})", events.size());
        } else {
            Set<String> allTopics = new HashSet<>(topics);
            topics.forEach(t -> allTopics.add(t + ".dlq"));
            events = repository.findAllByResolvedAndTopicIn(false, allTopics);
            log.info("Retrying unresolved events for specific topics (count={}): {}", events.size(), topics);
        }
        events.forEach(this::retry);
    }

    @Override
    public void retryEventsByDuration(int hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        List<FailedEvent> events = repository.findAllByResolvedAndCreatedAtAfter(false, startTime);
        events.forEach(this::retry);
    }

    private void retry(FailedEvent event) {
        String targetTopic = event.getTopic();
        if (targetTopic != null && targetTopic.endsWith(".dlq")) {
            targetTopic = targetTopic.substring(0, targetTopic.length() - 4);
        }
        
        log.info("Retrying event: {} to topic: {} with key: {}", event.getId(), targetTopic, event.getEventId());
        try {
            // We mark as resolved but don't delete. 
            // We MUST save before sending to Kafka to avoid a race condition:
            // if the consumer fails extremely fast, its logFailure call would be overwritten 
            // by this method's final save if we did it after send.
            event.setResolved(true);
            repository.save(event);
            
            // Try to parse the payload to its original object form to ensure correct serialization by KafkaTemplate
            Object payloadObject = parsePayload(event.getPayload(), event.getEventType());
            kafkaTemplate.send(targetTopic, event.getEventId(), payloadObject);
        } catch (Exception e) {
            log.error("Failed to retry event: {}", event.getId(), e);
            // If we couldn't even send to Kafka, it's NOT resolved
            event.setResolved(false);
            event.setRetryCount(event.getRetryCount() + 1);
            repository.save(event);
        }
    }

    private Object parsePayload(String payload, EventType type) {
        if (payload == null || payload.trim().isEmpty()) {
            return payload;
        }
        
        try {
            return switch (type) {
                case MESSAGE_INDEX_REQUESTED -> objectMapper.readValue(payload, MessageIndexRequestedEvent.class);
                case USER_INDEX_REQUESTED -> objectMapper.readValue(payload, UserIndexRequestedEvent.class);
                case USER_INDEX_DELETED -> objectMapper.readValue(payload, UserIndexDeletedEvent.class);
                default -> {
                    log.warn("No specific event class for event type: {}. Sending as raw JSON string.", type);
                    yield payload;
                }
            };
        } catch (JsonProcessingException e) {
            log.error("Error parsing payload for event type {}: {}", type, e.getMessage());
            return payload; // Fallback to raw string
        }
    }
}
