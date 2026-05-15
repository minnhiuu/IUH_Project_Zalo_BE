package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.searchservice.dto.request.SearchEventRequest;
import com.bondhub.searchservice.service.searchevent.SearchEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search/events")
@RequiredArgsConstructor
public class SearchEventController {

    private final SearchEventService searchEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> recordSearchEvent(@Valid @RequestBody SearchEventRequest request) {
        searchEventService.record(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
