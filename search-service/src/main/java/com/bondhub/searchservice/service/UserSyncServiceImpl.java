package com.bondhub.searchservice.service;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.client.AuthServiceClient;
import com.bondhub.searchservice.client.UserServiceClient;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.response.AccountResponse;
import com.bondhub.searchservice.dto.response.UserSyncResponse;
import com.bondhub.searchservice.dto.response.ReindexStatusResponse;
import com.bondhub.searchservice.enums.ReindexStep;
import com.bondhub.searchservice.enums.ReindexTaskStatus;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSyncServiceImpl implements UserSyncService {
    ElasticsearchOperations esOps;
    ElasticsearchProperties esProperties;
    AuthServiceClient authServiceClient;
    UserServiceClient userServiceClient;
    ReindexTaskTracker taskTracker;
    LocalizationUtil localizationUtil;

    @Override
    public String reindexAll() {
        if (taskTracker.isReindexRunning()) {
            throw new AppException(ErrorCode.EL_REINDEX_IN_PROGRESS);
        }

        String taskId = UUID.randomUUID().toString();
        long totalDocs = getUserCountFromUserService();

        updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.INITIALIZING, totalDocs, 0);

        CompletableFuture.runAsync(() -> performAsyncReindex(taskId, totalDocs));

        return taskId;
    }

    private void updateTaskStatus(String taskId, ReindexTaskStatus status, ReindexStep step, long total, long processed) {
        taskTracker.updateStatus(taskId, ReindexStatusResponse.builder()
                .taskId(taskId)
                .status(status)
                .total(total)
                .processed(processed)
                .percentage(step.getPercentage())
                .message(localizationUtil.getMessage(step.getMessageKey()))
                .build());
    }

    @Override
    public ReindexStatusResponse getReindexStatus(String taskId) {
        return taskTracker.getStatus(taskId);
    }

    private void performAsyncReindex(String taskId, long totalDocs) {
        try {
            log.info("Starting async reindex for task: {}", taskId);

            deletePhysicalIndexIfConflict();

            String newIndex = esProperties.getUserAlias() + "_" + System.currentTimeMillis();
            createPhysicalIndex(newIndex);

            fullSyncToIndex(newIndex, taskId, totalDocs);

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.SWITCHING_ALIAS, totalDocs, totalDocs);
            List<String> oldIndexes = switchAlias(newIndex);
            
            try {
                esOps.indexOps(IndexCoordinates.of(esProperties.getUserAlias())).refresh();
                log.info("Refreshed index alias '{}' after reindex", esProperties.getUserAlias());
            } catch (Exception e) {
                log.warn("Failed to refresh index after switch: {}", e.getMessage());
            }

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.CLEANING_UP, totalDocs, totalDocs);
            deleteOldIndexes(oldIndexes, newIndex);

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.FINALIZING, totalDocs, totalDocs);

            updateTaskStatus(taskId, ReindexTaskStatus.COMPLETED, ReindexStep.COMPLETED, totalDocs, totalDocs);

        } catch (Exception e) {
            log.error("Reindex failed for task {}: {}", taskId, e.getMessage());
            taskTracker.updateStatus(taskId, ReindexStatusResponse.builder()
                    .taskId(taskId)
                    .status(ReindexTaskStatus.FAILED)
                    .message(localizationUtil.getMessage("search.re-index.step.failed", e.getMessage()))
                    .build());
        }
    }

    private void createPhysicalIndex(String indexName) {
        IndexOperations ops = esOps.indexOps(IndexCoordinates.of(indexName));
        ops.create(ops.createSettings(UserIndex.class));
        ops.putMapping(ops.createMapping(UserIndex.class));
    }

    private void fullSyncToIndex(String indexName, String taskId, long totalDocs) {
        String lastId = null;
        long processed = 0;
        int batchSize = esProperties.getSync().getBatchSize();

        log.info("Starting full sync for task {} to index {}", taskId, indexName);

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

    private void bulkIndex(List<UserIndex> docs, String indexName) {
        if (docs.isEmpty()) return;

        List<IndexQuery> queries = docs.stream()
                .map(doc -> new IndexQueryBuilder()
                        .withId(doc.getId())
                        .withObject(doc)
                        .build())
                .toList();

        esOps.bulkIndex(queries, IndexCoordinates.of(indexName));
        log.debug("Bulk indexed {} documents to {}", docs.size(), indexName);
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

    private long getUserCountFromUserService() {
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

    private void deletePhysicalIndexIfConflict() {
        String alias = esProperties.getUserAlias();
        IndexOperations indexOps = esOps.indexOps(IndexCoordinates.of(alias));

        if (!indexOps.exists()) {
            log.debug("No index or alias named '{}' exists", alias);
            return;
        }

        try {
            var aliasInfo = indexOps.getAliases();

            if (aliasInfo.containsKey(alias)) {
                log.warn("Found physical index named '{}' that conflicts with alias name. Deleting it...", alias);
                indexOps.delete();
                log.info("Successfully deleted conflicting physical index '{}'", alias);
                Thread.sleep(1000);
            } else {
                log.debug("'{}' is already an alias, no conflict to resolve", alias);
            }
        } catch (Exception e) {
            log.error("Error while checking/deleting physical index '{}': {}", alias, e.getMessage(), e);
        }
    }

    private List<String> switchAlias(String newIndex) {
        String alias = esProperties.getUserAlias();
        IndexOperations newIndexOps = esOps.indexOps(IndexCoordinates.of(newIndex));
        List<String> oldIndexes = new ArrayList<>();

        try {
            IndexOperations aliasOps = esOps.indexOps(IndexCoordinates.of(alias));
            Map<String, Set<AliasData>> existingAliases = aliasOps.getAliases();

            if (!existingAliases.isEmpty()) {
                oldIndexes.addAll(existingAliases.keySet());
                AliasActions removeActions = new AliasActions();
                existingAliases.keySet().forEach(indexName -> {
                    log.info("Removing alias '{}' from index '{}'", alias, indexName);
                    removeActions.add(new AliasAction.Remove(
                            AliasActionParameters.builder()
                                    .withIndices(indexName)
                                    .withAliases(alias)
                                    .build()
                    ));
                });
                esOps.indexOps(IndexCoordinates.of(alias)).alias(removeActions);
                log.info("Removed alias '{}' from {} indices", alias, existingAliases.size());
            }
        } catch (Exception e) {
            log.warn("Failed to remove existing aliases (might not exist): {}", e.getMessage());
        }

        AliasActions addAction = new AliasActions();
        addAction.add(new AliasAction.Add(
                AliasActionParameters.builder()
                        .withIndices(newIndex)
                        .withAliases(alias)
                        .build()
        ));
        newIndexOps.alias(addAction);

        log.info("Successfully switched alias '{}' to index '{}'", alias, newIndex);
        return oldIndexes;
    }

    private void deleteOldIndexes(List<String> oldIndexes, String currentIndex) {
        try {
            if (oldIndexes.isEmpty()) {
                log.debug("No old indexes found to clean up");
                return;
            }

            String indexPrefix = esProperties.getUserAlias() + "_";

            List<String> sortedIndexes = oldIndexes.stream()
                    .filter(idx -> idx.startsWith(indexPrefix) && !idx.equals(currentIndex))
                    .sorted(Comparator.comparing((String idx) -> {
                        try {
                            String timestamp = idx.substring(indexPrefix.length());
                            return Long.parseLong(timestamp);
                        } catch (Exception e) {
                            return 0L;
                        }
                    }).reversed())
                    .toList();

            int retainCount = esProperties.getIndex().getRetainIndexCount();
            List<String> indexesToDelete = sortedIndexes.stream()
                    .skip(retainCount)
                    .toList();

            if (indexesToDelete.isEmpty()) {
                log.info("No old indexes to delete. Current count: {}, retain: {}",
                        sortedIndexes.size(), retainCount);
                return;
            }

            for (String indexName : indexesToDelete) {
                IndexOperations ops = esOps.indexOps(IndexCoordinates.of(indexName));
                ops.delete();
                log.info("Deleted old index: {}", indexName);
            }

            log.info("Cleanup completed. Deleted {} old indexes, retained {} recent indexes",
                    indexesToDelete.size(), Math.min(retainCount, sortedIndexes.size()));

        } catch (Exception e) {
            log.warn("Failed to cleanup old indexes: {}", e.getMessage(), e);
        }
    }
}
