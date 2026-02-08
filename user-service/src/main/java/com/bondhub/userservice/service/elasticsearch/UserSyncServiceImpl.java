package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.config.ElasticsearchProperties;
import com.bondhub.userservice.dto.response.AccountResponse;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.IndexedObjectInformation;
import org.springframework.data.elasticsearch.core.index.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSyncServiceImpl implements UserSyncService {

    ElasticsearchOperations esOps;
    UserRepository userRepository;
    UserMapper userMapper;
    ElasticsearchProperties esProperties;
    AuthServiceClient authServiceClient;

    @NonFinal
    ExecutorService reindexExecutor;

    @PostConstruct
    void init() {
        int parallelism = esProperties.getSync().getParallelism();
        int threads = (parallelism == 0) 
            ? Math.max(2, Runtime.getRuntime().availableProcessors())
            : parallelism;
        
        reindexExecutor = Executors.newFixedThreadPool(threads);
        log.info("Initialized reindex executor with {} threads", threads);
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down reindex executor...");
        reindexExecutor.shutdown();
        try {
            if (!reindexExecutor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Reindex executor did not terminate in 60s, forcing shutdown");
                reindexExecutor.shutdownNow();
                if (!reindexExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("Reindex executor did not terminate after forced shutdown");
                }
            } else {
                log.info("Reindex executor shut down gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted", e);
            reindexExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @Transactional
    public long reindexAll() {
        log.info("Starting complete reindex process...");
        
        // Clean up any conflicting physical index with the alias name
        deletePhysicalIndexIfConflict();
        
        LocalDateTime startedAt = LocalDateTime.now();
        String newIndex = esProperties.getUserAlias() + "_" + System.currentTimeMillis();

        createPhysicalIndex(newIndex);
        log.info("Created new physical index: {}", newIndex);

        long total = fullSyncToIndex(newIndex);
        log.info("Synced {} documents to new index", total);

        LocalDateTime cursor = startedAt;
        long replayed = 0;
        int replayRounds = 0;
        do {
            long roundReplayed = replayChanges(newIndex, cursor);
            replayed += roundReplayed;
            cursor = LocalDateTime.now();
            replayRounds++;
            log.info("Replay round {}: {} documents", replayRounds, roundReplayed);
        } while (replayed > 0);

        switchAlias(newIndex);
        log.info("Switched alias to new index: {}", newIndex);

        long totalIndexed = total + replayed;
        log.info("Reindex completed: {} total documents indexed", totalIndexed);
        return totalIndexed;
    }

    @Override
    public void recreateIndex() {
        String newIndex = esProperties.getUserAlias() + "_" + System.currentTimeMillis();
        createPhysicalIndex(newIndex);
        switchAlias(newIndex);
    }

    @Override
    public long syncAllFromMongo() {
        log.info("Starting full sync from MongoDB...");
        String newIndex = esProperties.getUserAlias() + "_" + System.currentTimeMillis();
        createPhysicalIndex(newIndex);
        long total = fullSyncToIndex(newIndex);
        switchAlias(newIndex);
        log.info("Sync completed: {} documents", total);
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
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

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

            List<UserIndex> batch = users.stream()
                    .map(user -> toUserIndexWithAccount(user, accountMap.get(user.getAccountId())))
                    .toList();

            String nextLastId = users.getLast().getId();

            tasks.add(CompletableFuture.runAsync(
                    () -> bulkIndexWithRetry(batch, indexName),
                    reindexExecutor
            ));

            lastId = nextLastId;
            total += users.size();

            if (total % 5000 == 0) {
                log.info("Progress: {} documents queued for indexing", total);
            }
        }

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        return total;
    }

    private long replayChanges(String indexName, LocalDateTime since) {
        List<User> changed = userRepository.findByLastModifiedAtAfter(since);
        if (changed.isEmpty()) return 0;

        List<String> accountIds = changed.stream()
                .map(User::getAccountId)
                .toList();
        
        Map<String, AccountResponse> accountMap = fetchAccountsBatch(accountIds);

        bulkIndexWithRetry(
                changed.stream()
                        .map(user -> toUserIndexWithAccount(user, accountMap.get(user.getAccountId())))
                        .toList(),
                indexName
        );
        return changed.size();
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

    private UserIndex toUserIndexWithAccount(User user, AccountResponse account) {
        UserIndex userIndex = userMapper.toUserIndex(user);
        
        if (account != null) {
            userIndex.setRole(account.role());
            userIndex.setPhoneNumber(account.phoneNumber());
        } else {
            log.warn("No account found for user {}, using defaults", user.getId());
            userIndex.setRole("USER");
            userIndex.setPhoneNumber(null);
        }
        
        return userIndex;
    }

    private void bulkIndexWithRetry(List<UserIndex> docs, String indexName) {
        List<UserIndex> remaining = docs;

        int maxRetry = esProperties.getSync().getMaxRetry();
        for (int attempt = 1; attempt <= maxRetry && !remaining.isEmpty(); attempt++) {
            if (attempt > 1) {
                log.warn("Retry attempt {} for {} documents", attempt, remaining.size());
            }
            remaining = bulkIndexOnce(remaining, indexName);
        }

        if (!remaining.isEmpty()) {
            log.error("Permanent bulk failures after {} attempts: {} documents",
                    maxRetry, remaining.size());
        }
    }

    private List<UserIndex> bulkIndexOnce(List<UserIndex> docs, String indexName) {
        List<IndexQuery> queries = docs.stream()
                .map(d -> new IndexQueryBuilder()
                        .withId(d.getId())
                        .withObject(d)
                        .build())
                .toList();

        IndexCoordinates coords = IndexCoordinates.of(
                indexName != null
                        ? indexName
                        : esProperties.getUserAlias()
        );

        List<IndexedObjectInformation> result =
                esOps.bulkIndex(queries, coords);

        Set<String> successIds = result.stream()
                .map(IndexedObjectInformation::id)
                .collect(Collectors.toSet());

        return docs.stream()
                .filter(d -> !successIds.contains(d.getId()))
                .toList();
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

    private void switchAlias(String newIndex) {
        String alias = esProperties.getUserAlias();
        AliasActions actions = new AliasActions();

        try {
            var existingAliases = esOps.indexOps(IndexCoordinates.of(alias)).getAliases();
            
            existingAliases.entrySet().stream()
                    .filter(entry -> {
                        String indexName = entry.getKey();
                        boolean hasAlias = entry.getValue().contains(alias);
                        boolean isUserIndex = indexName.startsWith(alias + "_");
                        boolean notSystemIndex = !indexName.startsWith(".");
                        
                        return hasAlias && (isUserIndex || notSystemIndex);
                    })
                    .forEach(entry -> {
                        String oldIndex = entry.getKey();
                        log.info("Removing alias '{}' from index '{}'", alias, oldIndex);
                        actions.add(new AliasAction.Remove(
                                AliasActionParameters.builder()
                                        .withIndices(oldIndex)
                                        .withAliases(alias)
                                        .build()
                        ));
                    });
        } catch (Exception e) {
            log.debug("No existing aliases found for '{}': {}", alias, e.getMessage());
        }

        actions.add(new AliasAction.Add(
                AliasActionParameters.builder()
                        .withIndices(newIndex)
                        .withAliases(alias)
                        .build()
        ));

        esOps.indexOps(IndexCoordinates.of(newIndex)).alias(actions);
        log.info("Successfully switched alias '{}' to index '{}'", alias, newIndex);
    }
}
