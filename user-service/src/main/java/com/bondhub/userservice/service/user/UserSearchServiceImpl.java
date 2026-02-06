package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.repository.UserSearchRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import com.bondhub.common.dto.SearchRequest;
import com.bondhub.common.utils.EsQueryBuilder;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.*;
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
        log.info("Syncing all users from MongoDB to Elasticsearch...");
        
        List<UserIndex> userIndices = userRepository.findAll().stream()
                .map(userMapper::toUserIndex)
                .collect(Collectors.toList());
        
        if (!userIndices.isEmpty()) {
            userSearchRepository.saveAll(userIndices);
            log.info("Successfully synced {} users.", userIndices.size());
            return userIndices.size();
        } else {
            log.info("No users found in MongoDB to sync.");
            return 0;
        }
    }
}
