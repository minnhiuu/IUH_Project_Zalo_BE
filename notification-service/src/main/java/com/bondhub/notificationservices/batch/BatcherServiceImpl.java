package com.bondhub.notificationservices.batch;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.notificationservices.enums.BatchWindowConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BatcherServiceImpl implements BatcherService {

    static final String LOCK_PREFIX = "batch:lock:";
    static final String LIST_PREFIX = "batch:";

    StringRedisTemplate stringRedisTemplate;
    BatchScheduler batchScheduler;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public boolean buffer(RawNotificationEvent event) {
        BatchWindowConfig cfg = BatchWindowConfig.of(event.getType());

        if (!cfg.isBatchable()) return false;

        try {
            String batchKey = buildBatchKey(event, cfg);
            String lockKey  = LOCK_PREFIX + batchKey;
            String listKey  = LIST_PREFIX + batchKey;

            Boolean isNewBatch = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(cfg.getWindowSeconds()));

            if (Boolean.TRUE.equals(isNewBatch)) {
                log.debug("New batch window opened: key={}, window={}s", batchKey, cfg.getWindowSeconds());
                batchScheduler.scheduleFlush(batchKey, cfg.getWindowSeconds());
            }

            String serialized = serialize(event);
            if (serialized != null) {
                stringRedisTemplate.opsForList().rightPush(listKey, serialized);
            }

            return true;
        } catch (Exception e) {
            log.error("Redis unavailable, falling back to direct delivery for type={}", event.getType(), e);
            return false;
        }
    }

    @Override
    public boolean isBatchableType(NotificationType type) {
        return BatchWindowConfig.of(type).isBatchable();
    }

    private String buildBatchKey(RawNotificationEvent event, BatchWindowConfig cfg) {
        String key = event.getType().name() + ":" + event.getRecipientId();
        if (cfg.isIncludeReferenceInKey() && event.getReferenceId() != null) {
            key += ":" + event.getReferenceId();
        }
        return key;
    }

    private String serialize(RawNotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event", e);
            return null;
        }
    }
}
