package com.bondhub.searchservice.service;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.response.ReindexStatusResponse;
import com.bondhub.searchservice.enums.ReindexStep;
import com.bondhub.searchservice.enums.ReindexTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class AbstractSyncService<T> {

    protected final ElasticsearchOperations esOps;
    protected final ElasticsearchProperties esProperties;
    protected final ReindexTaskTracker taskTracker;
    protected final LocalizationUtil localizationUtil;

    protected AbstractSyncService(
            ElasticsearchOperations esOps,
            ElasticsearchProperties esProperties,
            ReindexTaskTracker taskTracker,
            LocalizationUtil localizationUtil) {
        this.esOps = esOps;
        this.esProperties = esProperties;
        this.taskTracker = taskTracker;
        this.localizationUtil = localizationUtil;
    }

    protected abstract String getAlias();
    
    protected abstract Class<T> getIndexClass();
    
    protected abstract long getTotalDocsCount();
    
    protected abstract void fullSyncToIndex(String indexName, String taskId, long totalDocs);

    public String reindexAll() {
        if (taskTracker.isReindexRunning()) {
            throw new AppException(ErrorCode.EL_REINDEX_IN_PROGRESS);
        }

        String taskId = UUID.randomUUID().toString();
        long totalDocs = getTotalDocsCount();

        updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.INITIALIZING, totalDocs, 0);

        CompletableFuture.runAsync(() -> performAsyncReindex(taskId, totalDocs));

        return taskId;
    }

    protected void updateTaskStatus(String taskId, ReindexTaskStatus status, ReindexStep step, long total, long processed) {
        taskTracker.updateStatus(taskId, ReindexStatusResponse.builder()
                .taskId(taskId)
                .status(status)
                .total(total)
                .processed(processed)
                .percentage(step.getPercentage())
                .message(localizationUtil.getMessage(step.getMessageKey()))
                .build());
    }

    public ReindexStatusResponse getReindexStatus(String taskId) {
        return taskTracker.getStatus(taskId);
    }

    private void performAsyncReindex(String taskId, long totalDocs) {
        String alias = getAlias();
        try {
            log.info("Starting async reindex for alias '{}', task: {}", alias, taskId);

            deletePhysicalIndexIfConflict(alias);

            String newIndex = alias + "_" + System.currentTimeMillis();
            createPhysicalIndex(newIndex);

            fullSyncToIndex(newIndex, taskId, totalDocs);

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.SWITCHING_ALIAS, totalDocs, totalDocs);
            List<String> oldIndexes = switchAlias(alias, newIndex);
            
            try {
                esOps.indexOps(IndexCoordinates.of(alias)).refresh();
                log.info("Refreshed index alias '{}' after reindex", alias);
            } catch (Exception e) {
                log.warn("Failed to refresh index after switch: {}", e.getMessage());
            }

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.CLEANING_UP, totalDocs, totalDocs);
            deleteOldIndexes(alias, oldIndexes, newIndex);

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, ReindexStep.FINALIZING, totalDocs, totalDocs);

            updateTaskStatus(taskId, ReindexTaskStatus.COMPLETED, ReindexStep.COMPLETED, totalDocs, totalDocs);

        } catch (Exception e) {
            log.error("Reindex failed for alias '{}', task {}: {}", alias, taskId, e.getMessage());
            taskTracker.updateStatus(taskId, ReindexStatusResponse.builder()
                    .taskId(taskId)
                    .status(ReindexTaskStatus.FAILED)
                    .message(localizationUtil.getMessage("search.re-index.step.failed", e.getMessage()))
                    .build());
        }
    }

    protected void createPhysicalIndex(String indexName) {
        IndexOperations ops = esOps.indexOps(IndexCoordinates.of(indexName));
        ops.create(ops.createSettings(getIndexClass()));
        ops.putMapping(ops.createMapping(getIndexClass()));
    }

    protected void bulkIndex(List<T> docs, String indexName) {
        if (docs.isEmpty()) return;

        List<IndexQuery> queries = docs.stream()
                .map(doc -> {
                    // We assume the model has an getId() method or use reflection/ObjectUtils
                    // For safety in this generic context, we can use ElasticsearchOperations' default ID extraction
                    return new IndexQueryBuilder()
                            .withObject(doc)
                            .build();
                })
                .toList();

        esOps.bulkIndex(queries, IndexCoordinates.of(indexName));
        log.debug("Bulk indexed {} documents to {}", docs.size(), indexName);
    }

    private void deletePhysicalIndexIfConflict(String alias) {
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
            log.error("Error while checking/deleting physical index '{}': {}", alias, e.getMessage());
        }
    }

    private List<String> switchAlias(String alias, String newIndex) {
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
            log.warn("Failed to remove existing aliases for '{}' (might not exist): {}", alias, e.getMessage());
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

    private void deleteOldIndexes(String alias, List<String> oldIndexes, String currentIndex) {
        try {
            if (oldIndexes.isEmpty()) {
                return;
            }

            String indexPrefix = alias + "_";

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

            for (String indexName : indexesToDelete) {
                IndexOperations ops = esOps.indexOps(IndexCoordinates.of(indexName));
                ops.delete();
                log.info("Deleted old index: {}", indexName);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old indexes for alias '{}': {}", alias, e.getMessage());
        }
    }
}
