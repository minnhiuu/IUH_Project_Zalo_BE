package com.bondhub.searchservice.service.failevent;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.searchservice.dto.request.FailedEventFilter;
import com.bondhub.searchservice.dto.response.FailedEventResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface FailedEventService {
    void logFailure(String eventId, EventType eventType, String topic, Integer partition, Long offset, String payload, String errorMessage, String stackTrace, int retryCount);
    
    PageResponse<List<FailedEventResponse>> getEventsPaged(FailedEventFilter filter);

    long countEventsByResolved(boolean resolved);

    long countEventsByResolvedAndTopics(boolean resolved, Collection<String> topics);
    
    void updateResolved(String id, boolean resolved);

    FailedEventResponse getEventById(String id);

    void retryEvent(String id);
    
    void retryEvents(List<String> ids);

    void retryAllEvents(Collection<String> topics);

    void retryEventsByDuration(int hours);
}
