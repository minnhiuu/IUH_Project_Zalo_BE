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
import org.springframework.data.domain.Page;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSearchServiceImpl implements UserSearchService {

    ElasticsearchOperations elasticsearchOperations;
    UserMapper userMapper;
    UserSearchRepository userSearchRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;

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
        log.info("Syncing all users from MongoDB to Elasticsearch using batch processing...");

        long totalSynced = 0;
        int pageSize = 500;
        int pageNumber = 0;
        
        Page<User> userPage;

        do {
            userPage = userRepository.findAll(PageRequest.of(pageNumber, pageSize));

            List<UserIndex> batch = userPage.getContent().stream()
                    .map(userMapper::toUserIndex)
                    .collect(Collectors.toList());

            if (!batch.isEmpty()) {
                userSearchRepository.saveAll(batch);
                totalSynced += batch.size();
                log.info("Synced batch {}: {} users. Total so far: {}", pageNumber + 1, batch.size(), totalSynced);
            }

            pageNumber++;
        } while (userPage.hasNext());

        log.info("Full sync completed. Total users synced: {}", totalSynced);
        return totalSynced;
    }


}
