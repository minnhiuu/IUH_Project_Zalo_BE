package com.bondhub.searchservice.service;

import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.dto.request.RecentSearchRequest;
import com.bondhub.searchservice.dto.response.RecentHistoryResponse;
import com.bondhub.searchservice.dto.response.RecentSearchResponse;
import com.bondhub.searchservice.enums.SearchType;
import com.bondhub.searchservice.mapper.RecentSearchMapper;
import com.bondhub.searchservice.model.redis.RecentSearch;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecentSearchServiceImpl implements RecentSearchService {

    RedisTemplate<String, Object> redisTemplate;
    RecentSearchMapper recentSearchMapper;
    SecurityUtil securityUtil;
 
    static String PREFIX_ITEM = "recent_search:items:";
    static String PREFIX_QUERY = "recent_search:queries:";
    static int MAX_ITEMS = 15;
    static long EXPIRATION_DAYS = 30;

    @Override
    public void addSearchItem(RecentSearchRequest request) {
        String userId = securityUtil.getCurrentUserId();
 
        if (request.id().equals(userId)) {
            log.info("Skipping saving self search to history for user: {}", userId);
            return;
        }
 
        String key = (request.type() == SearchType.KEYWORD)
                ? PREFIX_QUERY + userId
                : PREFIX_ITEM + userId;

        long now = System.currentTimeMillis();
        RecentSearch newItem = recentSearchMapper.toModel(request);
        newItem.setTimestamp(now);

        Set<Object> currentHistory = redisTemplate.opsForZSet().range(key, 0, -1);
        if (currentHistory != null) {
            currentHistory.stream()
                    .map(obj -> (RecentSearch) obj)
                    .filter(oldItem -> oldItem.getId().equals(newItem.getId())
                            || (request.type() == SearchType.KEYWORD && oldItem.getName().equalsIgnoreCase(newItem.getName())))
                    .forEach(oldItem -> redisTemplate.opsForZSet().remove(key, oldItem));
        }

        if (request.type() != SearchType.KEYWORD) {
            String queryKey = PREFIX_QUERY + userId;
            Set<Object> queries = redisTemplate.opsForZSet().range(queryKey, 0, -1);
            if (queries != null) {
                queries.stream()
                        .map(obj -> (RecentSearch) obj)
                        .filter(q -> {
                            String keyword = q.getName().toLowerCase();
                            String itemName = request.name().toLowerCase();
                            return itemName.contains(keyword) || keyword.contains(itemName);
                        })
                        .forEach(q -> {
                            redisTemplate.opsForZSet().remove(queryKey, q);
                            log.info("Removed redundant query '{}' after selecting item '{}'", q.getName(), request.name());
                        });
            }
        }

        redisTemplate.opsForZSet().add(key, newItem, now);
        Long size = redisTemplate.opsForZSet().size(key);
        if (size != null && size > MAX_ITEMS) {
            redisTemplate.opsForZSet().removeRange(key, 0, size - (MAX_ITEMS + 1));
        }

        redisTemplate.expire(key, EXPIRATION_DAYS, TimeUnit.DAYS);
    }

    @Override
    public List<RecentSearchResponse> getRecentItems() {
        return fetchFromRedis(PREFIX_ITEM + securityUtil.getCurrentUserId());
    }

    @Override
    public List<RecentSearchResponse> getRecentQueries() {
        return fetchFromRedis(PREFIX_QUERY + securityUtil.getCurrentUserId());
    }

    @Override
    public RecentHistoryResponse getRecentHistory() {
        String userId = securityUtil.getCurrentUserId();
        return RecentHistoryResponse.builder()
                .items(fetchFromRedis(PREFIX_ITEM + userId))
                .queries(fetchFromRedis(PREFIX_QUERY + userId))
                .build();
    }

    private List<RecentSearchResponse> fetchFromRedis(String key) {
        Set<Object> results = redisTemplate.opsForZSet().reverseRange(key, 0, MAX_ITEMS - 1);
        if (results == null || results.isEmpty()) return Collections.emptyList();

        return results.stream()
                .map(obj -> (RecentSearch) obj)
                .map(recentSearchMapper::toResponse)
                .toList();
    }

    @Override
    public void removeItem(String itemId, SearchType type) {
        String userId = securityUtil.getCurrentUserId();
        String key = (type == SearchType.KEYWORD) ? PREFIX_QUERY + userId : PREFIX_ITEM + userId;

        Set<Object> currentHistory = redisTemplate.opsForZSet().range(key, 0, -1);
        if (currentHistory != null) {
            currentHistory.stream()
                    .map(obj -> (RecentSearch) obj)
                    .filter(item -> item.getId().equals(itemId))
                    .forEach(item -> {
                        redisTemplate.opsForZSet().remove(key, item);
                        log.info("Removed {} from {}", itemId, key);
                    });
        }
    }

    @Override
    public void clearAllHistory() {
        String userId = securityUtil.getCurrentUserId();
        redisTemplate.delete(PREFIX_ITEM + userId);
        redisTemplate.delete(PREFIX_QUERY + userId);
        log.info("User {} cleared both items and queries history", userId);
    }
}
