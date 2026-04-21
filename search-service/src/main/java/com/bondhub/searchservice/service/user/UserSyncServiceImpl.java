package com.bondhub.searchservice.service.user;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.client.AuthServiceClient;
import com.bondhub.searchservice.client.UserServiceClient;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.response.AccountResponse;
import com.bondhub.searchservice.dto.response.UserSyncResponse;
import com.bondhub.searchservice.enums.ReindexStep;
import com.bondhub.searchservice.enums.ReindexTaskStatus;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;
import com.bondhub.searchservice.service.AbstractSyncService;
import com.bondhub.searchservice.service.ReindexTaskTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserSyncServiceImpl extends AbstractSyncService<UserIndex> implements UserSyncService {

    private final AuthServiceClient authServiceClient;
    private final UserServiceClient userServiceClient;

    public UserSyncServiceImpl(
            ElasticsearchOperations esOps,
            ElasticsearchProperties esProperties,
            ReindexTaskTracker taskTracker,
            LocalizationUtil localizationUtil,
            AuthServiceClient authServiceClient,
            UserServiceClient userServiceClient) {
        super(esOps, esProperties, taskTracker, localizationUtil);
        this.authServiceClient = authServiceClient;
        this.userServiceClient = userServiceClient;
    }

    @Override
    protected String getAlias() {
        return esProperties.getUserAlias();
    }

    @Override
    protected Class<UserIndex> getIndexClass() {
        return UserIndex.class;
    }

    @Override
    protected long getTotalDocsCount() {
        try {
            ApiResponse<Long> response = userServiceClient.getUserCount();
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.warn("Failed to get user count from user-service: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    protected void fullSyncToIndex(String indexName, String taskId, long totalDocs) {
        String lastId = null;
        long processed = 0;
        int batchSize = esProperties.getSync().getBatchSize();

        log.info("Starting full sync for users, task {} to index {}", taskId, indexName);

        while (true) {
            ApiResponse<List<UserSyncResponse>> response = userServiceClient.getUsersBatch(lastId, batchSize);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                break;
            }

            List<UserSyncResponse> users = response.data();

            List<String> accountIds = users.stream()
                    .map(UserSyncResponse::accountId)
                    .filter(Objects::nonNull)
                    .toList();
            Map<String, AccountResponse> accountMap = fetchAccountsBatch(accountIds);

            List<UserIndex> docs = users.stream()
                    .map(user -> {
                        AccountResponse account = user.accountId() != null
                                ? accountMap.get(user.accountId()) : null;

                        return UserIndex.builder()
                                .id(user.id())
                                .accountId(user.accountId())
                                .fullName(user.fullName())
                                .avatar(user.avatar())
                                .phoneNumber(account != null ? account.phoneNumber() : null)
                                .role(account != null ? account.role() : null)
                                .createdAt(LocalDateTime.now())
                                .build();
                    })
                    .toList();

            bulkIndex(docs, indexName);

            lastId = users.getLast().id();
            processed += users.size();

            // Determine current step based on progress ratio
            ReindexStep currentStep = ReindexStep.SYNCING_START;
            if (totalDocs > 0) {
                double currentRatio = (double) processed / totalDocs;
                if (currentRatio > 0.8) currentStep = ReindexStep.DATA_COMPLETE;
                else if (currentRatio > 0.4) currentStep = ReindexStep.SYNCING_MID;
            }

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, currentStep, totalDocs, processed);

            if (processed % 5000 == 0) {
                log.info("Task {}: Progress {}/{}", taskId, processed, totalDocs);
            }
        }
    }

    private Map<String, AccountResponse> fetchAccountsBatch(List<String> accountIds) {
        try {
            ApiResponse<List<AccountResponse>> response = authServiceClient.getAccountsByIds(accountIds);
            if (response != null && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(AccountResponse::id, acc -> acc));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch accounts batch: {}", e.getMessage());
        }
        return Map.of();
    }
}
