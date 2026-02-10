package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.userservice.dto.response.elasticsearch.ReindexStatusResponse;

public interface UserSyncService {
    String reindexAll();
    ReindexStatusResponse getReindexStatus(String taskId);
}
