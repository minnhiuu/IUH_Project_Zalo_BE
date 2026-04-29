package com.bondhub.searchservice.service.recentsearch;

import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.dto.request.RecentSearchRequest;
import com.bondhub.searchservice.dto.response.RecentHistoryResponse;
import com.bondhub.searchservice.dto.response.RecentSearchResponse;
import com.bondhub.searchservice.enums.SearchType;
import com.bondhub.searchservice.mapper.RecentSearchMapper;
import com.bondhub.searchservice.model.mongodb.RecentSearch;
import com.bondhub.searchservice.repository.RecentSearchRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecentSearchServiceImpl implements RecentSearchService {

    RecentSearchRepository recentSearchRepository;
    RecentSearchMapper recentSearchMapper;
    SecurityUtil securityUtil;

    static int MAX_ITEMS = 15;

    @Override
    @Transactional
    public void addSearchItem(RecentSearchRequest request) {
        String userId = securityUtil.getCurrentUserId();

        if (request.id().equals(userId)) {
            log.info("Skipping saving self search to history for user: {}", userId);
            return;
        }

        if (request.type() == SearchType.KEYWORD) {
            recentSearchRepository.findByUserIdAndNameIgnoreCaseAndType(userId, request.name(), SearchType.KEYWORD)
                    .ifPresent(recentSearchRepository::delete);
        } else {
            recentSearchRepository.findByUserIdAndTargetIdAndType(userId, request.id(), request.type())
                    .ifPresent(recentSearchRepository::delete);
            
            removeRedundantQueries(userId, request.name());
        }

        RecentSearch newItem = recentSearchMapper.toModel(request);
        newItem.setUserId(userId);
        newItem.setTimestamp(System.currentTimeMillis());
        recentSearchRepository.save(newItem);

        long count = recentSearchRepository.countByUserIdAndType(userId, request.type());
        if (count > MAX_ITEMS) {
            List<RecentSearch> history = recentSearchRepository.findAllByUserIdAndTypeOrderByTimestampAsc(userId, request.type());
            long toDelete = count - MAX_ITEMS;
            for (int i = 0; i < toDelete && i < history.size(); i++) {
                recentSearchRepository.delete(history.get(i));
            }
        }
    }

    private void removeRedundantQueries(String userId, String itemName) {
        String lowerItemName = itemName.toLowerCase();
        List<RecentSearch> queries = recentSearchRepository.findAllByUserIdAndTypeOrderByTimestampDesc(userId, SearchType.KEYWORD);
        
        queries.stream()
                .filter(q -> {
                    String keyword = q.getName().toLowerCase();
                    return lowerItemName.contains(keyword) || keyword.contains(lowerItemName);
                })
                .forEach(q -> {
                    recentSearchRepository.delete(q);
                    log.info("Removed redundant query '{}' after selecting item '{}'", q.getName(), itemName);
                });
    }

    @Override
    public List<RecentSearchResponse> getRecentItems() {
        String userId = securityUtil.getCurrentUserId();
        return recentSearchRepository.findAllByUserIdAndTypeInOrderByTimestampDesc(userId, List.of(SearchType.USER, SearchType.GROUP))
                .stream()
                .limit(MAX_ITEMS)
                .map(recentSearchMapper::toResponse)
                .toList();
    }

    @Override
    public List<RecentSearchResponse> getRecentQueries() {
        String userId = securityUtil.getCurrentUserId();
        return recentSearchRepository.findAllByUserIdAndTypeOrderByTimestampDesc(userId, SearchType.KEYWORD).stream()
                .limit(MAX_ITEMS)
                .map(recentSearchMapper::toResponse)
                .toList();
    }

    @Override
    public RecentHistoryResponse getRecentHistory() {
        String userId = securityUtil.getCurrentUserId();
        
        List<RecentSearchResponse> queries = recentSearchRepository.findAllByUserIdAndTypeOrderByTimestampDesc(userId, SearchType.KEYWORD)
                .stream()
                .limit(MAX_ITEMS)
                .map(recentSearchMapper::toResponse)
                .toList();

        List<RecentSearchResponse> items = recentSearchRepository.findAllByUserIdAndTypeInOrderByTimestampDesc(userId, List.of(SearchType.USER, SearchType.GROUP))
                .stream()
                .limit(MAX_ITEMS)
                .map(recentSearchMapper::toResponse)
                .toList();
                
        return RecentHistoryResponse.builder()
                .items(items)
                .queries(queries)
                .build();
    }

    @Override
    @Transactional
    public void removeItem(String itemId, SearchType type) {
        String userId = securityUtil.getCurrentUserId();
        recentSearchRepository.deleteByUserIdAndTargetIdAndType(userId, itemId, type);
        log.info("Removed {} item with id {} for user {}", type, itemId, userId);
    }

    @Override
    @Transactional
    public void clearAllHistory() {
        String userId = securityUtil.getCurrentUserId();
        recentSearchRepository.deleteAllByUserId(userId);
        log.info("User {} cleared both items and queries history from DB", userId);
    }
}
