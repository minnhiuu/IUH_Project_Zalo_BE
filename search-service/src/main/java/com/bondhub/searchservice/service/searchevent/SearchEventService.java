package com.bondhub.searchservice.service.searchevent;

import com.bondhub.searchservice.dto.request.SearchEventRequest;

public interface SearchEventService {
    void record(SearchEventRequest request);
}
