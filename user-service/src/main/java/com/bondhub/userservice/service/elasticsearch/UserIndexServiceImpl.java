package com.bondhub.userservice.service.elasticsearch;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.userservice.config.ElasticsearchProperties;
import com.bondhub.userservice.dto.request.UserIndexRequest;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserIndexServiceImpl implements UserIndexService {

    ElasticsearchOperations esOps;
    UserRepository userRepository;
    UserMapper userMapper;
    ElasticsearchProperties esProperties;

    @NonFinal
    ExecutorService realtimeExecutor;

    @PostConstruct
    void init() {
        int threads = esProperties.getIndex().getRealtimeThreads();
        realtimeExecutor = Executors.newFixedThreadPool(threads);
        log.info("Initialized realtime executor with {} threads", threads);
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down realtime executor...");
        realtimeExecutor.shutdown();
        try {
            if (!realtimeExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Realtime executor did not terminate in 30s, forcing shutdown");
                realtimeExecutor.shutdownNow();
                if (!realtimeExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("Realtime executor did not terminate after forced shutdown");
                }
            } else {
                log.info("Realtime executor shut down gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted", e);
            realtimeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void indexUser(UserIndexRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                User user = userRepository.findById(request.userId())
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                UserIndex userIndex = userMapper.toUserIndex(user);
                
                if (request.phoneNumber() != null) {
                    userIndex.setPhoneNumber(request.phoneNumber());
                }
                if (request.role() != null) {
                    userIndex.setRole(request.role().name());
                }

                esOps.save(userIndex, IndexCoordinates.of(esProperties.getUserAlias()));
                log.debug("Successfully indexed user: {}", request.userId());
            } catch (Exception e) {
                log.error("Failed to index user: {}", request.userId(), e);
            }
        }, realtimeExecutor);
    }

    @Override
    public void deleteByUserId(String userId) {
        CompletableFuture.runAsync(() -> {
            try {
                esOps.delete(userId, IndexCoordinates.of(esProperties.getUserAlias()));
                log.debug("Successfully deleted user from index: {}", userId);
            } catch (Exception e) {
                log.error("Failed to delete user from index: {}", userId, e);
            }
        }, realtimeExecutor);
    }
}
