package com.bondhub.searchservice.service.index.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.*;
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
public abstract class AbstractSyncService<T> extends AbstractSearchIndexService implements SearchIndexSynchronizer {

    protected final ReindexTaskTracker taskTracker;

    protected AbstractSyncService(
            ElasticsearchOperations esOps,
            ElasticsearchClient esClient,
            ElasticsearchProperties esProperties,
            ReindexTaskTracker taskTracker,
            LocalizationUtil localizationUtil) {
        super(esOps, esClient, esProperties, localizationUtil);
        this.taskTracker = taskTracker;
    }

    @Override
    public abstract Class<T> getIndexClass();
    
    protected abstract long getTotalDocsCount();
    
    protected abstract void fullSyncToIndex(String indexName, String taskId, long totalDocs);

    @Override
    public DataComparisonResponse compareWithDatabase() {
        return DataComparisonResponse.builder()
                .elasticsearchCount(0)
                .databaseCount(0)
                .status(DataSyncStatus.IN_SYNC)
                .recommendation("Override compareWithDatabase in subclass to enable this feature")
                .build();
    }

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

    @Override
    public ReindexStatusResponse getReindexStatus(String taskId) {
        return taskTracker.getStatus(taskId);
    }

    @Override
    public Object getDocument(String id) {
        Object doc = esOps.get(id, getIndexClass(), IndexCoordinates.of(getAlias()));
        if (doc == null) {
            throw new AppException(ErrorCode.EL_DOCUMENT_NOT_FOUND);
        }
        return doc;
    }

    @Override
    public IndexOperationResponse switchAlias(String targetIndexName) {
        String alias = getAlias();
        try {
            switchAlias(alias, targetIndexName);
            return new IndexOperationResponse(localizationUtil.getMessage("search.alias.switch.success"), targetIndexName);
        } catch (Exception e) {
            log.error("Failed to switch alias '{}' to index '{}': {}", alias, targetIndexName, e.getMessage());
            throw new AppException(ErrorCode.EL_CLUSTER_UNHEALTHY);
        }
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
                .map(doc -> new IndexQueryBuilder().withObject(doc).build())
                .toList();

        esOps.bulkIndex(queries, IndexCoordinates.of(indexName));
        log.debug("Bulk indexed {} documents to {}", docs.size(), indexName);
    }

    private void deletePhysicalIndexIfConflict(String alias) {
        IndexOperations indexOps = esOps.indexOps(IndexCoordinates.of(alias));

        if (!indexOps.exists()) return;

        try {
            var aliasInfo = indexOps.getAliases();
            if (aliasInfo.containsKey(alias)) {
                log.warn("Found physical index named '{}' that conflicts with alias name. Deleting it...", alias);
                indexOps.delete();
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.error("Error while checking/deleting physical index '{}': {}", alias, e.getMessage());
        }
    }

    private List<String> switchAlias(String alias, String newIndex) {
        List<String> oldIndexes = new ArrayList<>();
        AliasActions combinedActions = new AliasActions();

        try {
            IndexOperations aliasOps = esOps.indexOps(IndexCoordinates.of(alias));
            Map<String, Set<AliasData>> existingAliases = aliasOps.getAliases();

            if (!existingAliases.isEmpty()) {
                oldIndexes.addAll(existingAliases.keySet());
                existingAliases.keySet().forEach(indexName -> {
                    combinedActions.add(new AliasAction.Remove(
                            AliasActionParameters.builder()
                                    .withIndices(indexName)
                                    .withAliases(alias)
                                    .build()
                    ));
                });
            }
        } catch (Exception e) {
            log.warn("Note: No existing indices found for alias '{}' to remove.", alias);
        }

        combinedActions.add(new AliasAction.Add(
                AliasActionParameters.builder()
                        .withIndices(newIndex)
                        .withAliases(alias)
                        .build()
        ));

        esOps.indexOps(IndexCoordinates.of(alias)).alias(combinedActions);
        return oldIndexes;
    }

    private void deleteOldIndexes(String alias, List<String> oldIndexes, String currentIndex) {
        try {
            if (oldIndexes.isEmpty()) return;

            String indexPrefix = alias + "_";
            List<String> sortedIndexes = oldIndexes.stream()
                    .filter(idx -> idx.startsWith(indexPrefix) && !idx.equals(currentIndex))
                    .sorted(Comparator.comparing((String idx) -> {
                        try {
                            return Long.parseLong(idx.substring(indexPrefix.length()));
                        } catch (Exception e) {
                            return 0L;
                        }
                    }).reversed())
                    .toList();

            int retainCount = esProperties.getIndex().getRetainIndexCount();
            List<String> indexesToDelete = sortedIndexes.stream().skip(retainCount).toList();

            for (String indexName : indexesToDelete) {
                esOps.indexOps(IndexCoordinates.of(indexName)).delete();
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old indexes for alias '{}': {}", alias, e.getMessage());
        }
    }
}
