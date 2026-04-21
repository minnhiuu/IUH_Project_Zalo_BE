package com.bondhub.searchservice.service.index.core;

import com.bondhub.searchservice.dto.response.ReindexStatusResponse;
import com.bondhub.searchservice.enums.ReindexTaskStatus;
import com.bondhub.searchservice.enums.SearchIndexType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReindexTaskTracker {
    private final Map<String, ReindexStatusResponse> tasks = new ConcurrentHashMap<>();

    public void updateStatus(String taskId, ReindexStatusResponse status) {
        tasks.put(taskId, status);
    }

    public ReindexStatusResponse getStatus(String taskId) {
        return tasks.get(taskId);
    }

    public void removeTask(String taskId) {
        tasks.remove(taskId);
    }

    public boolean isReindexRunning(SearchIndexType type) {
        return tasks.values().stream()
                .anyMatch(task -> task.type() == type && task.status() == ReindexTaskStatus.RUNNING);
    }
}
