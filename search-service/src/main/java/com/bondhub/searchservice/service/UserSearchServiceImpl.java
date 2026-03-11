package com.bondhub.searchservice.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;
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
    SecurityUtil securityUtil;
    ElasticsearchProperties esProperties;

    @Override
    public PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable) {

        if (!StringUtils.hasText(keyword)) {
            return PageResponse.empty(pageable);
        }

        String searchTerm = keyword.trim();
        String currentUserId = securityUtil.getCurrentUserId();

        Query query = Query.of(q -> q.bool(b -> {
            b.should(s -> s.term(t ->
                    t.field("phoneNumber")
                            .value(searchTerm)
            ));

            b.should(s -> s.match(m ->
                    m.field("fullName")
                            .query(searchTerm)
                            .boost(5.0f)
            ));

            b.should(s -> s.match(m ->
                    m.field("fullName.fuzzy")
                            .query(searchTerm)
                            .fuzziness("1")
                            .prefixLength(0)
                            .maxExpansions(50)
                            .fuzzyTranspositions(true)
                            .boost(2.0f)
            ));

            b.should(s -> s.multiMatch(mm ->
                    mm.fields("fullName", "fullName.fuzzy")
                            .query(searchTerm)
                            .fuzziness("1")
                            .prefixLength(0)
                            .maxExpansions(50)
                            .type(TextQueryType.BestFields)
                            .boost(1.5f)
            ));

            b.minimumShouldMatch("1");

            b.filter(f -> f.term(t ->
                    t.field("role")
                            .value(Role.USER.name())
            ));

            if (currentUserId != null) {
                b.filter(f -> f.bool(fb -> fb
                        .should(s -> s.bool(nb -> nb.mustNot(mn -> mn.term(t -> t.field("id").value(currentUserId)))))
                        .should(s -> s.term(t -> t.field("phoneNumber").value(searchTerm)))
                        .minimumShouldMatch("1")
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
                hit -> toUserSummaryResponse(hit.getContent(), searchTerm)
        );
    }

    private UserSummaryResponse toUserSummaryResponse(UserIndex userIndex, String searchTerm) {
        boolean isPhoneMatch = searchTerm.equals(userIndex.getPhoneNumber());
        return UserSummaryResponse.builder()
                .id(userIndex.getId())
                .fullName(userIndex.getFullName())
                .avatar(userIndex.getAvatar())
                .phoneNumber(isPhoneMatch ? userIndex.getPhoneNumber() : null)
                .build();
    }
}
