package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.repository.UserSearchRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.bondhub.common.dto.SearchRequest;
import com.bondhub.common.utils.EsQueryBuilder;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.*;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.userservice.dto.request.UserIndexRequest;
import com.bondhub.userservice.repository.UserRepository;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSearchServiceImpl implements UserSearchService {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_RETRY = 3;
    private static final int PARALLELISM = Math.max(2, Runtime.getRuntime().availableProcessors());

    ElasticsearchOperations elasticsearchOperations;
    UserMapper userMapper;
    UserSearchRepository userSearchRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;
    
    ExecutorService indexExecutor = Executors.newFixedThreadPool(PARALLELISM);

    @Override
    public PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return PageResponse.empty(pageable);
        }

        String currentAccountId = securityUtil.getCurrentAccountId();

        SearchRequest request = SearchRequest.builder()
                .searchTerm(keyword)
                .searchFields(List.of("fullName", "phoneNumber"))
                .fuzziness("1")
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .build();

        NativeQuery query = EsQueryBuilder.buildNativeQuery(
                request,
                "fullName",
                builder -> {
                    if (currentAccountId != null) {
                        builder.mustNot(mn -> mn.term(t -> t.field("accountId").value(currentAccountId)));
                    }
                    builder.must(m -> m.term(t -> t.field("role").value(Role.USER.name())));
                }
        );

        log.info("Searching users for keyword: [{}] (Excluding accountId: {})", keyword, currentAccountId);

        SearchHits<UserIndex> searchHits = elasticsearchOperations.search(query, UserIndex.class);
        SearchPage<UserIndex> page = SearchHitSupport.searchPageFor(searchHits, query.getPageable());

        return PageResponse.fromPage(page, hit -> userMapper.toUserSummaryResponse(hit.getContent()));
    }

    @Override
    public void saveToToIndex(UserIndex userIndex) {
        log.info("Saving user to Elasticsearch index: {}", userIndex.getId());
        userSearchRepository.save(userIndex);
    }

    @Override
    public void deleteFromIndex(String userId) {
        log.info("Deleting user from Elasticsearch index: {}", userId);
        userSearchRepository.deleteById(userId);
    }

    @Override
    public void indexUserToElasticsearch(UserIndexRequest request) {
        log.info("Indexing user to Elasticsearch: userId={}, phoneNumber={}, role={}",
                request.userId(), request.phoneNumber(), request.role());
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        syncUserToIndex(user, request.phoneNumber(), request.role());
    }

    private void syncUserToIndex(User user, String phoneNumber, Role role) {
        try {
            UserIndex userIndex = userMapper.toUserIndex(user);

            if (phoneNumber != null) {
                userIndex.setPhoneNumber(phoneNumber);
            }

            if (role != null) {
                userIndex.setRole(role.name());
            } else if (userIndex.getRole() == null) {
                userIndex.setRole(Role.USER.name());
            }

            saveToToIndex(userIndex);
        } catch (Exception e) {
            log.error("Failed to sync user to Elasticsearch index: {}", user.getId(), e);
        }
    }

    @Override
    @Transactional
    public long reIndexAll() {
        log.info("Starting full search re-index process for Users...");
        reCreateIndex();
        long count = syncAllFromMongo();
        log.info("Full search re-index process completed successfully. Total: {}", count);
        return count;
    }

    @Override
    public void reCreateIndex() {
        log.info("Re-creating Elasticsearch index for Users...");
        IndexOperations indexOps = elasticsearchOperations.indexOps(UserIndex.class);
        
        if (indexOps.exists()) {
            indexOps.delete();
            log.info("Index deleted.");
        }
        
        indexOps.create();
        indexOps.putMapping(indexOps.createMapping(UserIndex.class));
        log.info("Index created with latest mappings.");
    }

    @Override
    public long syncAllFromMongo() {
        log.info("Starting optimized full sync MongoDB -> Elasticsearch (parallel cursor-based)");

        long totalSynced = 0;
        String lastId = null;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        while (true) {
            List<User> users;
            if (lastId == null) {
                users = userRepository.findAllByOrderByIdAsc(PageRequest.of(0, BATCH_SIZE));
            } else {
                users = userRepository.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, BATCH_SIZE));
            }

            if (users.isEmpty()) {
                break;
            }

            List<UserIndex> batch = users.stream()
                    .map(userMapper::toUserIndex)
                    .collect(Collectors.toList());

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean success = bulkIndexWithRetry(batch);
                if (!success) {
                    log.error("Failed to index batch ending with ID: {}", users.get(users.size() - 1).getId());
                }
            }, indexExecutor);
            
            futures.add(future);
            
            totalSynced += users.size();
            lastId = users.getLast().getId();
            
            log.info("Queued batch: size={}, totalProcessed={}", users.size(), totalSynced);

            if (futures.size() >= PARALLELISM * 2) {
                waitForSome(futures);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Parallel full sync completed. Total users processed: {}", totalSynced);
        
        return totalSynced;
    }

    private void waitForSome(List<CompletableFuture<Void>> futures) {
        futures.removeIf(CompletableFuture::isDone);
        if (futures.size() >= PARALLELISM * 2) {
            try {
                futures.getFirst().get(1, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Error while waiting for indexing task in back-pressure control", e);
            }
        }
    }

    private boolean bulkIndexWithRetry(List<UserIndex> batch) {
        int attempt = 0;
        while (attempt < MAX_RETRY) {
            try {
                bulkIndex(batch);
                return true;
            } catch (Exception e) {
                attempt++;
                log.warn("Bulk index failed (attempt {}/{}). Retrying...", attempt, MAX_RETRY, e);
                try { 
                    TimeUnit.MILLISECONDS.sleep(200L * attempt); 
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private void bulkIndex(List<UserIndex> users) {
        List<IndexQuery> queries = users.stream()
                .map(user -> new IndexQueryBuilder()
                        .withId(user.getId())
                        .withObject(user)
                        .build())
                .collect(Collectors.toList());

        elasticsearchOperations.bulkIndex(queries, elasticsearchOperations.getIndexCoordinatesFor(UserIndex.class));
    }
}
