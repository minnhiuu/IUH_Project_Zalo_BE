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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSearchServiceImpl implements UserSearchService {

    ElasticsearchOperations elasticsearchOperations;
    UserMapper userMapper;
    UserSearchRepository userSearchRepository;
    SecurityUtil securityUtil;

    @Override
    public PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return PageResponse.empty(pageable);
        }

        String currentAccountId = securityUtil.getCurrentAccountId();

        Pageable unsortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.unsorted());

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.should(s -> s.match(m -> m
                            .field("fullName")
                            .query(keyword)
                            .fuzziness("1")
                    ));
                    b.should(s -> s.term(t -> t
                            .field("phoneNumber")
                            .value(keyword)
                    ));
                    b.minimumShouldMatch("1");
                    if (currentAccountId != null) {
                        b.mustNot(mn -> mn.term(t -> t
                                .field("accountId")
                                .value(currentAccountId)
                        ));
                    }
                    b.must(m -> m.term(t -> t
                            .field("role")
                            .value(Role.USER.name())
                    ));
                    return b;
                }))
                .withPageable(unsortedPageable)
                .build();
        log.info("Searching users for keyword: [{}] (Excluding accountId: {})", keyword, currentAccountId);

        SearchHits<UserIndex> searchHits = elasticsearchOperations.search(query, UserIndex.class);
        SearchPage<UserIndex> page = SearchHitSupport.searchPageFor(searchHits, unsortedPageable);

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
}