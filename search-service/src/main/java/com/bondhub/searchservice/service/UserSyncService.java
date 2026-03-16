package com.bondhub.searchservice.service;

import com.bondhub.searchservice.dto.response.ReindexStatusResponse;

public interface UserSyncService {
    String reindexAll();
    ReindexStatusResponse getReindexStatus(String taskId);
}
