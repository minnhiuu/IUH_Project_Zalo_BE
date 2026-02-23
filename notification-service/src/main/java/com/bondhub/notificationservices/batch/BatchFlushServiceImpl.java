package com.bondhub.notificationservices.batch;

import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.bondhub.notificationservices.service.delivery.DeliveryService;
import com.bondhub.notificationservices.service.preference.UserPreferenceService;
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

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BatchFlushServiceImpl implements BatchFlushService {

    static final String LOCK_PREFIX = "batch:lock:";
    static final String LIST_PREFIX = "batch:";

    StringRedisTemplate stringRedisTemplate;
    DeliveryService deliveryService;
    UserPreferenceService userPreferenceService;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void flush(String batchKey) {
        String listKey = LIST_PREFIX + batchKey;
        String lockKey = LOCK_PREFIX + batchKey;

        List<String> rawList = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        stringRedisTemplate.delete(listKey);
        stringRedisTemplate.delete(lockKey);

        if (rawList == null || rawList.isEmpty()) {
            log.debug("Flush no-op: empty list for batchKey={}", batchKey);
            return;
        }

        List<RawNotificationEvent> events = rawList.stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .toList();

        if (events.isEmpty()) return;

        RawNotificationEvent first = events.getFirst();
        List<String> actorIds = events.stream()
                .map(RawNotificationEvent::getActorId)
                .distinct()
                .toList();

        int actorCount  = actorIds.size();
        int othersCount = actorCount - 1;
        String locale   = userPreferenceService.getLocale(first.getRecipientId());

        List<Map<String, Object>> rawPayloads = events.stream()
                .map(e -> {
                    Map<String, Object> p = new HashMap<>(
                            e.getPayload() != null ? e.getPayload() : Collections.emptyMap()
                    );
                    p.put("actorId",     e.getActorId());
                    p.put("referenceId", e.getReferenceId());
                    p.put("occurredAt",  e.getOccurredAt() != null ? e.getOccurredAt().toString() : null);
                    return p;
                }).toList();

        BatchedNotificationEvent batched = BatchedNotificationEvent.builder()
                .recipientId(first.getRecipientId())
                .type(first.getType())
                .actorIds(actorIds)
                .actorCount(actorCount)
                .firstActorId(first.getActorId())
                .firstActorName(first.getActorName() != null ? first.getActorName() : first.getActorId())
                .firstActorAvatar(first.getActorAvatar())
                .othersCount(othersCount)
                .locale(locale)
                .rawPayloads(rawPayloads)
                .batchedAt(LocalDateTime.now())
                .build();

        deliveryService.deliver(batched);
        log.info("Batch flushed: key={}, actors={}", batchKey, actorCount);
    }

    private RawNotificationEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, RawNotificationEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialize failed: {}", json, e);
            return null;
        }
    }
}
