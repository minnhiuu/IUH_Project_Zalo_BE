package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.config.ElasticsearchProperties;
import com.bondhub.userservice.dto.response.AccountResponse;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    UserRepository userRepository;
    ElasticsearchProperties esProperties;
    AuthServiceClient authServiceClient;

    @Override
    @Transactional
    public long reindexAll() {
        log.info("Starting complete reindex process with bulk indexing...");
        
        deletePhysicalIndexIfConflict();
        
        String newIndex = esProperties.getUserAlias() + "_" + System.currentTimeMillis();
        createPhysicalIndex(newIndex);
        log.info("Created new physical index: {}", newIndex);

        long total = fullSyncToIndex(newIndex);
        log.info("Indexed {} documents to Elasticsearch", total);
        
        List<String> oldIndexes = switchAlias(newIndex);
        log.info("Reindex completed successfully! Alias '{}' now points to '{}'",
                esProperties.getUserAlias(), newIndex);
        
        deleteOldIndexes(oldIndexes, newIndex);
        return total;
    }

    private void createPhysicalIndex(String indexName) {
        IndexOperations ops = esOps.indexOps(IndexCoordinates.of(indexName));
        ops.create(ops.createSettings(UserIndex.class));
        ops.putMapping(ops.createMapping(UserIndex.class));
    }

    private long fullSyncToIndex(String indexName) {
        String lastId = null;
        long total = 0;

        log.info("Starting full sync from MongoDB to Elasticsearch (target index: {})", indexName);

        while (true) {
            List<User> users = lastId == null
                    ? userRepository.findAllByOrderByIdAsc(PageRequest.of(0, esProperties.getSync().getBatchSize()))
                    : userRepository.findByIdGreaterThanOrderByIdAsc(
                    lastId, PageRequest.of(0, esProperties.getSync().getBatchSize()));

            if (users.isEmpty()) break;

            List<String> accountIds = users.stream()
                    .map(User::getAccountId)
                    .toList();
            Map<String, AccountResponse> accountMap = fetchAccountsBatch(accountIds);

            List<UserIndex> docs = users.stream()
                    .map(user -> {
                        AccountResponse account = accountMap.get(user.getAccountId());
                        return UserIndex.builder()
                                .id(user.getId())
                                .accountId(user.getAccountId())
                                .fullName(user.getFullName())
                                .avatar(user.getAvatar())
                                .phoneNumber(account != null ? account.phoneNumber() : null)
                                .role(account != null ? account.role() : null)
                                .createdAt(LocalDateTime.now())
                                .build();
                    })
                    .toList();

            bulkIndex(docs, indexName);

            lastId = users.getLast().getId();
            total += users.size();

            if (total % 5000 == 0) {
                log.info("Progress: {} users indexed", total);
            }
        }

        log.info("Full sync completed: {} users indexed to Elasticsearch", total);
        return total;
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