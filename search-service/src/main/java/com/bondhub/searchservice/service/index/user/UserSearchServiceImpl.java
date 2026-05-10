package com.bondhub.searchservice.service.index.user;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.friendservice.UserSearchContextRequest;
import com.bondhub.common.dto.client.friendservice.UserSearchContextResponse;
import com.bondhub.common.utils.PhoneUtil;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.client.FriendServiceClient;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;
import com.bondhub.searchservice.service.index.user.ranking.UserSearchRankingContext;
import com.bondhub.searchservice.service.index.user.ranking.UserSearchRankingStrategy;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSearchServiceImpl implements UserSearchService {
        private static final int MIN_RERANK_CANDIDATE_SIZE = 100;
        private static final int RERANK_WINDOW_MULTIPLIER = 5;

        ElasticsearchOperations esOps;
        SecurityUtil securityUtil;
        ElasticsearchProperties esProperties;
        S3UtilV2 s3UtilV2;
        FriendServiceClient friendServiceClient;
        UserSearchRankingStrategy rankingStrategy;

        @Override
        public PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable) {

                if (!StringUtils.hasText(keyword)) {
                        return PageResponse.empty(pageable);
                }

                String searchTerm = keyword.trim();
                String phoneSearchTerm = PhoneUtil.normalizeVnPhone(searchTerm).orElse(searchTerm);
                String currentUserId = securityUtil.getCurrentUserId();

                Query query = Query.of(q -> q.bool(b -> {
                        b.should(s -> s.term(t -> t.field("phoneNumber")
                                        .value(phoneSearchTerm)
                                        .boost(20.0f)));

                        b.should(s -> s.match(m -> m.field("fullName")
                                        .query(searchTerm)
                                        .boost(5.0f)));

                        b.should(s -> s.match(m -> m.field("fullName.fuzzy")
                                        .query(searchTerm)
                                        .fuzziness("1")
                                        .prefixLength(0)
                                        .maxExpansions(50)
                                        .fuzzyTranspositions(true)
                                        .boost(2.0f)));

                        b.should(s -> s.multiMatch(mm -> mm.fields("fullName", "fullName.fuzzy")
                                        .query(searchTerm)
                                        .fuzziness("1")
                                        .prefixLength(0)
                                        .maxExpansions(50)
                                        .type(TextQueryType.BestFields)
                                        .boost(1.5f)));

                        b.minimumShouldMatch("1");

                        b.filter(f -> f.term(t -> t.field("role")
                                        .value(Role.USER.name())));

                        if (currentUserId != null) {
                                b.filter(f -> f.bool(fb -> fb
                                                .should(s -> s.bool(nb -> nb.mustNot(mn -> mn
                                                .term(t -> t.field("id").value(currentUserId)))))
                                                .should(s -> s.term(t -> t.field("phoneNumber").value(phoneSearchTerm)))
                                                .minimumShouldMatch("1")));
                        }

                        return b;
                }));

                int candidateSize = calculateCandidateSize(pageable);
                Pageable finalPageable = PageRequest.of(
                                0,
                                candidateSize,
                                Sort.by(
                                                Sort.Order.desc("_score"),
                                                Sort.Order.desc("createdAt")));

                NativeQuery nativeQuery = NativeQuery.builder()
                                .withQuery(query)
                                .withPageable(finalPageable)
                                .build();

                SearchHits<UserIndex> hits = esOps.search(
                                nativeQuery,
                                UserIndex.class,
                                IndexCoordinates.of(esProperties.getUserAlias()));

                List<RankedUserSearchHit> rankedHits = rerankHits(
                                hits.getSearchHits(),
                                searchTerm,
                                currentUserId);

                List<RankedUserSearchHit> pagedHits = paginateRankedHits(rankedHits, pageable);

                return PageResponse.<List<UserSummaryResponse>>builder()
                                .data(pagedHits.stream()
                                                .map(hit -> toUserSummaryResponse(hit.userIndex(), searchTerm))
                                                .toList())
                                .page(pageable.getPageNumber())
                                .totalPages(calculateTotalPages(rankedHits.size(), pageable.getPageSize()))
                                .limit(pageable.getPageSize())
                                .totalItems(rankedHits.size())
                                .build();
        }

        private UserSummaryResponse toUserSummaryResponse(UserIndex userIndex, String searchTerm) {
                boolean isPhoneSearch = PhoneUtil.isValidVnPhone(searchTerm);
                String baseUrl = s3UtilV2.getS3BaseUrl();
                return UserSummaryResponse.builder()
                                .id(userIndex.getId())
                                .fullName(userIndex.getFullName())
                                .avatar(userIndex.getAvatar() != null ? baseUrl + userIndex.getAvatar() : null)
                                .phoneNumber(isPhoneSearch ? userIndex.getPhoneNumber() : null)
                                .build();
        }

        @Override
        public List<UserSummaryResponse> findUsersByPhones(List<String> phones) {
                if (phones == null || phones.isEmpty()) {
                        return Collections.emptyList();
                }

                // ES terms query for exact match on phoneNumber keyword field
                Query query = Query.of(q -> q.bool(b -> {
                        b.filter(f -> f.terms(t -> t
                                        .field("phoneNumber")
                                        .terms(tv -> tv.value(phones.stream()
                                                        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                        .toList()))));
                        b.filter(f -> f.term(t -> t.field("role").value(Role.USER.name())));
                        return b;
                }));

                NativeQuery nativeQuery = NativeQuery.builder()
                                .withQuery(query)
                                .withPageable(PageRequest.of(0, phones.size()))
                                .build();

                SearchHits<UserIndex> hits = esOps.search(
                                nativeQuery,
                                UserIndex.class,
                                IndexCoordinates.of(esProperties.getUserAlias()));

                List<UserSummaryResponse> results = new ArrayList<>();
                for (SearchHit<UserIndex> hit : hits.getSearchHits()) {
                        UserIndex u = hit.getContent();
                        results.add(UserSummaryResponse.builder()
                                        .id(u.getId())
                                        .fullName(u.getFullName())
                                        .avatar(u.getAvatar())
                                        .phoneNumber(u.getPhoneNumber())
                                        .build());
                }
                return results;
        }

        private int calculateCandidateSize(Pageable pageable) {
                int requestedEnd = (pageable.getPageNumber() + 1) * pageable.getPageSize();
                return Math.max(requestedEnd * RERANK_WINDOW_MULTIPLIER, MIN_RERANK_CANDIDATE_SIZE);
        }

        private List<RankedUserSearchHit> rerankHits(
                        List<SearchHit<UserIndex>> searchHits,
                        String searchTerm,
                        String currentUserId) {

                if (searchHits == null || searchHits.isEmpty()) {
                        return Collections.emptyList();
                }

                Map<String, UserSearchContextResponse> contextByUserId = fetchUserSearchContext(searchHits);

                return searchHits.stream()
                                .map(hit -> toRankedHit(hit, searchTerm, currentUserId, contextByUserId))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .sorted(Comparator
                                                .comparingDouble(RankedUserSearchHit::finalScore).reversed()
                                                .thenComparing(Comparator.comparingDouble(RankedUserSearchHit::elasticsearchScore).reversed())
                                                .thenComparing(RankedUserSearchHit::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                                .toList();
        }

        private Map<String, UserSearchContextResponse> fetchUserSearchContext(List<SearchHit<UserIndex>> searchHits) {
                List<String> userIds = searchHits.stream()
                                .map(SearchHit::getContent)
                                .map(UserIndex::getId)
                                .filter(StringUtils::hasText)
                                .distinct()
                                .toList();

                if (userIds.isEmpty()) {
                        return Collections.emptyMap();
                }

                try {
                        ApiResponse<List<UserSearchContextResponse>> response = friendServiceClient.getUserSearchContext(
                                        UserSearchContextRequest.builder()
                                                        .targetUserIds(userIds)
                                                        .build());

                        if (response == null || response.data() == null) {
                                return Collections.emptyMap();
                        }

                        return response.data().stream()
                                        .filter(context -> StringUtils.hasText(context.userId()))
                                        .collect(Collectors.toMap(
                                                        UserSearchContextResponse::userId,
                                                        Function.identity(),
                                                        (existing, replacement) -> existing));
                } catch (Exception e) {
                        log.warn("Failed to fetch user search relationship context, falling back to Elasticsearch ranking: {}",
                                        e.getMessage());
                        return Collections.emptyMap();
                }
        }

        private Optional<RankedUserSearchHit> toRankedHit(
                        SearchHit<UserIndex> hit,
                        String searchTerm,
                        String currentUserId,
                        Map<String, UserSearchContextResponse> contextByUserId) {

                UserIndex userIndex = hit.getContent();
                UserSearchContextResponse context = contextByUserId.get(userIndex.getId());

                // TODO: Apply search visibility filtering when friend-service exposes explicit
                // search/profile privacy semantics. The current block model is channel-based
                // (message/call/story), so a BlockList record alone is not enough to hide users
                // from global search safely.

                UserSearchRankingContext rankingContext = toRankingContext(searchTerm, userIndex, context);
                double finalScore = rankingStrategy.calculateFinalScore(hit.getScore(), rankingContext, currentUserId);

                return Optional.of(new RankedUserSearchHit(
                                userIndex,
                                hit.getScore(),
                                finalScore,
                                userIndex.getCreatedAt()));
        }

        private UserSearchRankingContext toRankingContext(
                        String searchTerm,
                        UserIndex userIndex,
                        UserSearchContextResponse context) {

                return new UserSearchRankingContext(
                                context != null ? context.friendshipStatus() : null,
                                context != null ? context.requestedBy() : null,
                                context != null && context.mutualFriendsCount() != null ? context.mutualFriendsCount() : 0,
                                context != null && context.sharedGroupsCount() != null ? context.sharedGroupsCount() : 0,
                                context != null && Boolean.TRUE.equals(context.inContact()),
                                context != null && context.contactScore() != null ? context.contactScore() : 0.0,
                                rankingStrategy.isExactPhoneMatch(searchTerm, userIndex.getPhoneNumber()));
        }

        private List<RankedUserSearchHit> paginateRankedHits(List<RankedUserSearchHit> rankedHits, Pageable pageable) {
                int fromIndex = Math.toIntExact(Math.min(pageable.getOffset(), rankedHits.size()));
                int toIndex = Math.min(fromIndex + pageable.getPageSize(), rankedHits.size());
                return rankedHits.subList(fromIndex, toIndex);
        }

        private int calculateTotalPages(int totalItems, int pageSize) {
                if (totalItems == 0 || pageSize <= 0) {
                        return 0;
                }
                return (int) Math.ceil((double) totalItems / pageSize);
        }

        private record RankedUserSearchHit(
                UserIndex userIndex,
                double elasticsearchScore,
                double finalScore,
                LocalDateTime createdAt) {
        }
}
