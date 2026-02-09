package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.config.ElasticsearchProperties;
import com.bondhub.userservice.dto.request.UserIndexRequest;
import com.bondhub.userservice.dto.response.AccountResponse;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.publisher.UserIndexEventPublisher;
import com.bondhub.userservice.repository.UserRepository;
import com.bondhub.userservice.service.kafka.KafkaConsumerMonitorService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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
    UserIndexEventPublisher userIndexEventPublisher;
    KafkaConsumerMonitorService kafkaMonitorService;
    KafkaTopicProperties kafkaTopicProperties;

    @Override
    @Transactional
    public long reindexAll() {
        log.info("Starting complete reindex process via Kafka...");
        
        deletePhysicalIndexIfConflict();
        
        LocalDateTime startedAt = LocalDateTime.now();
        String newIndex = esProperties.getUserAlias() + "_" + System.currentTimeMillis();

        createPhysicalIndex(newIndex);
        log.info("Created new physical index: {}", newIndex);

        long total = fullSyncToIndex(newIndex);
        log.info("Published {} events to Kafka for indexing", total);

        LocalDateTime cursor = startedAt;
        long replayed = 0;
        int replayRounds = 0;
        do {
            long roundReplayed = replayChanges(newIndex, cursor);
            replayed += roundReplayed;
            cursor = LocalDateTime.now();
            replayRounds++;
            if (roundReplayed > 0) {
                log.info("Replay round {}: {} documents published", replayRounds, roundReplayed);
            }
        } while (replayed > 0 && replayRounds < 10);

        long totalPublished = total + replayed;
        log.info("📤 Published {} total events to Kafka", totalPublished);
        
        String consumerGroup = "user-search-indexer-group";
        String topic = kafkaTopicProperties.getUserEvents().getIndexRequested();
        int maxWaitSeconds = 300;
        
        boolean success = kafkaMonitorService.waitForLagZero(consumerGroup, topic, maxWaitSeconds);
        
        if (!success) {
            long remainingLag = kafkaMonitorService.getConsumerLag(consumerGroup, topic);
            log.error("❌ Reindex timeout! Consumer lag still: {} messages", remainingLag);
            log.error("New index '{}' created but alias NOT switched. Manual intervention required.", newIndex);
            log.info("After consumers finish, manually call: switchAliasToLatestIndex()");
            throw new RuntimeException("Reindex timeout: consumers did not finish processing within " + maxWaitSeconds + " seconds");
        }
        
        log.info("✅ All {} events processed by consumers. Switching alias...", totalPublished);
        switchAlias(newIndex);
        log.info("🎉 Reindex completed successfully! Alias '{}' now points to '{}'", 
                esProperties.getUserAlias(), newIndex);
        
        return totalPublished;
    }

    public void switchAliasToLatestIndex() {
        String alias = esProperties.getUserAlias();
        try {
            var existingAliases = esOps.indexOps(IndexCoordinates.of(alias)).getAliases();
            String latestIndex = existingAliases.keySet().stream()
                    .filter(idx -> idx.startsWith(alias + "_"))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            
            if (latestIndex != null) {
                log.info("Switching alias '{}' to latest index '{}'", alias, latestIndex);
                switchAlias(latestIndex);
            } else {
                log.warn("No index found with pattern {}_, cannot switch alias", alias);
            }
        } catch (Exception e) {
            log.error("Failed to switch alias: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to switch alias", e);
        }
    }

    private void createPhysicalIndex(String indexName) {
        IndexOperations ops = esOps.indexOps(IndexCoordinates.of(indexName));
        ops.create(ops.createSettings(UserIndex.class));
        ops.putMapping(ops.createMapping(UserIndex.class));
    }

    private long fullSyncToIndex(String indexName) {
        String lastId = null;
        long total = 0;

        log.info("Starting full sync from MongoDB to Kafka (target index: {})", indexName);

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

            for (User user : users) {
                AccountResponse account = accountMap.get(user.getAccountId());

                UserIndexRequest request = UserIndexRequest.builder()
                        .userId(user.getId())
                        .phoneNumber(account != null ? account.phoneNumber() : null)
                        .role(account != null ? Enum.valueOf(Role.class, account.role()) : null)
                        .build();

                userIndexEventPublisher.publishIndexRequestBatch(request);
            }

            lastId = users.getLast().getId();
            total += users.size();

            if (total % 5000 == 0) {
                log.info("Progress: {} users published to Kafka", total);
            }
        }

        log.info("Full sync completed: {} users published to Kafka", total);
        return total;
    }

    private long replayChanges(String indexName, LocalDateTime since) {
        List<User> changed = userRepository.findByLastModifiedAtAfter(since);
        if (changed.isEmpty()) return 0;

        log.info("Replaying {} changes since {}", changed.size(), since);

        List<String> accountIds = changed.stream()
                .map(User::getAccountId)
                .toList();
        Map<String, AccountResponse> accountMap = fetchAccountsBatch(accountIds);

        for (User user : changed) {
            AccountResponse account = accountMap.get(user.getAccountId());

            UserIndexRequest request = UserIndexRequest.builder()
                    .userId(user.getId())
                    .phoneNumber(account != null ? account.phoneNumber() : null)
                    .role(account != null ? Enum.valueOf(Role.class, account.role()) : null)
                    .build();

            userIndexEventPublisher.publishIndexRequestBatch(request);
        }

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
