package com.bondhub.userservice.service.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.config.ElasticsearchProperties;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSearchServiceImpl implements UserSearchService {

    ElasticsearchOperations esOps;
    UserMapper userMapper;
    SecurityUtil securityUtil;
    ElasticsearchProperties esProperties;

    @Override
    public PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable) {

        if (!StringUtils.hasText(keyword)) {
            return PageResponse.empty(pageable);
        }

        String searchTerm = keyword.trim();
        String currentAccountId = securityUtil.getCurrentAccountId();

        Query query = Query.of(q -> q.bool(b -> {
            // Exact phone number match - highest priority
            b.should(s -> s.term(t ->
                    t.field("phoneNumber")
                            .value(searchTerm)
                            .boost(10.0f)
            ));

            // Exact full name match (edge n-gram) - high priority
            b.should(s -> s.match(m ->
                    m.field("fullName")
                            .query(searchTerm)
                            .boost(5.0f)
            ));

            // Fuzzy match on normalized fullName - medium priority
            b.should(s -> s.match(m ->
                    m.field("fullName.fuzzy")
                            .query(searchTerm)
                            .fuzziness("AUTO")
                            .prefixLength(0)  // Allow fuzzy in entire string
                            .maxExpansions(50)
                            .fuzzyTranspositions(true)  // Allow character swaps
                            .boost(2.0f)
            ));

            // Multi-match with fuzzy across all text fields
            b.should(s -> s.multiMatch(mm ->
                    mm.fields("fullName", "fullName.fuzzy")
                            .query(searchTerm)
                            .fuzziness("AUTO")
                            .prefixLength(0)
                            .maxExpansions(50)
                            .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                            .boost(1.5f)
            ));

            b.minimumShouldMatch("1");

            b.filter(f -> f.term(t ->
                    t.field("role")
                            .value(Role.USER.name())
            ));

            if (currentAccountId != null) {
                b.mustNot(m -> m.term(t ->
                        t.field("accountId")
                                .value(currentAccountId)
                ));
            }

            return b;
        }));

        Pageable finalPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.desc("_score"),
                        Sort.Order.desc("createdAt")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(finalPageable)
                .build();

        SearchHits<UserIndex> hits = esOps.search(
                nativeQuery,
                UserIndex.class,
                IndexCoordinates.of(esProperties.getUserAlias())
        );

        SearchPage<UserIndex> page =
                SearchHitSupport.searchPageFor(hits, finalPageable);

        return PageResponse.fromPage(
                page,
                hit -> userMapper.toUserSummaryResponse(hit.getContent())
        );
    }

}

