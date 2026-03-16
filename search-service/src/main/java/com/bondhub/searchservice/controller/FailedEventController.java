package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.dto.response.FailedEventResponse;
import com.bondhub.searchservice.service.FailedEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search/elasticsearch/failed-events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FailedEventController {

    private final FailedEventService failedEventService;
    private final LocalizationUtil localizationUtil;

    @GetMapping("/paged")
    public ResponseEntity<ApiResponse<PageResponse<List<FailedEventResponse>>>> getFailedEventsPaged(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(failedEventService.getEventsByResolved(resolved, keyword, hours, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FailedEventResponse>> getFailedEvent(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(failedEventService.getEventById(id)));
    }

    @PatchMapping("/{id}/resolved")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateResolved(
            @PathVariable String id,
            @RequestParam boolean resolved) {
        failedEventService.updateResolved(id, resolved);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.status.update.success")
        )));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryEvent(@PathVariable String id) {
        failedEventService.retryEvent(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.retry.single.success")
        )));
    }

    @PostMapping("/retry-bulk")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryFailedEventsBulk(@RequestBody List<String> ids) {
        failedEventService.retryEvents(ids);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.retry.bulk.success")
        )));
    }

    @PostMapping("/retry-all")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryAllEvents() {
        failedEventService.retryAllEvents();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.retry.all.success")
        )));
    }

    @PostMapping("/retry-duration")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryEventsByDuration(@RequestParam(defaultValue = "24") int hours) {
        failedEventService.retryEventsByDuration(hours);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", localizationUtil.getMessage("search.retry.duration.success", new Object[]{hours})
        )));
    }
}
