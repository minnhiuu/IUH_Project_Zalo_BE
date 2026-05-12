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
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.client.FriendServiceClient;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.response.UserSearchResponse;
import com.bondhub.searchservice.dto.response.UserSearchScoreBreakdown;
import com.bondhub.searchservice.model.elasticsearch.UserIndex;
import com.bondhub.searchservice.model.mongodb.UserInteractionFeature;
import com.bondhub.searchservice.repository.mongodb.UserInteractionFeatureRepository;
import com.bondhub.searchservice.service.index.user.ranking.UserSearchRankingContext;
import com.bondhub.searchservice.service.index.user.ranking.UserSearchRankingStrategy;
import com.bondhub.searchservice.service.index.user.ranking.UserSearchRelationshipLabel;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
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
        private static final int MAX_RERANK_CANDIDATE_SIZE = 300;
        private static final int RERANK_WINDOW_MULTIPLIER = 5;
        private static final int MAX_USER_SEARCH_PAGE_SIZE = 50;

        ElasticsearchOperations esOps;
        SecurityUtil securityUtil;
        ElasticsearchProperties esProperties;
        S3UtilV2 s3UtilV2;
        FriendServiceClient friendServiceClient;
        UserInteractionFeatureRepository userInteractionFeatureRepository;
        UserSearchRankingStrategy rankingStrategy;
        LocalizationUtil localizationUtil;
        Environment environment;

        @Override
        public PageResponse<List<UserSearchResponse>> searchUsersWithMetadata(
                        String keyword,
                        int page,
                        int size,
                        boolean debug) {
                if (debug) {
                        ensureDebugAllowed();
                }
                Pageable pageable = toPageable(page, size);
                PageResponse<List<RankedUserSearchHit>> rankedPage = searchRankedUsers(keyword, pageable);

                return PageResponse.<List<UserSearchResponse>>builder()
                                .data(rankedPage.data().stream()
                                                .map(hit -> toUserSearchResponse(hit, keyword, debug))
                                                .toList())
                                .page(rankedPage.page())
                                .totalPages(rankedPage.totalPages())
                                .limit(rankedPage.limit())
                                .totalItems(rankedPage.totalItems())
                                .build();
        }

        @Override
        public PageResponse<List<UserSummaryResponse>> searchUsers(String keyword, Pageable pageable) {
                pageable = normalizePageable(pageable);
                PageResponse<List<RankedUserSearchHit>> rankedPage = searchRankedUsers(keyword, pageable);

                return PageResponse.<List<UserSummaryResponse>>builder()
                                .data(rankedPage.data().stream()
                                                .map(hit -> toUserSummaryResponse(hit.userIndex(), keyword))
                                                .toList())
                                .page(rankedPage.page())
                                .totalPages(rankedPage.totalPages())
                                .limit(rankedPage.limit())
                                .totalItems(rankedPage.totalItems())
                                .build();
        }

        private PageResponse<List<RankedUserSearchHit>> searchRankedUsers(String keyword, Pageable pageable) {

                if (!StringUtils.hasText(keyword)) {
                        return PageResponse.empty(pageable);
                }

                long startedAt = System.nanoTime();
                String searchTerm = keyword.trim();
                String phoneSearchTerm = normalizePhoneSearchTerm(searchTerm);
                boolean phoneLikeSearch = isPhoneLike(searchTerm);
                String currentUserId = securityUtil.getCurrentUserId();

                Query query = Query.of(q -> q.bool(b -> {
                        b.should(s -> s.term(t -> t.field("phoneNumber")
                                        .value(phoneSearchTerm)
                                        .boost(phoneLikeSearch ? 80.0f : 20.0f)));

                        b.should(s -> s.term(t -> t.field("phoneNumber.normalized")
                                        .value(phoneSearchTerm)
                                        .boost(phoneLikeSearch ? 100.0f : 25.0f)));

                        b.should(s -> s.term(t -> t.field("fullName.keyword")
                                        .value(searchTerm)
                                        .caseInsensitive(true)
                                        .boost(phoneLikeSearch ? 5.0f : 30.0f)));

                        b.should(s -> s.matchPhrasePrefix(m -> m.field("fullName.prefix")
                                        .query(searchTerm)
                                        .boost(phoneLikeSearch ? 3.0f : 18.0f)));

                        b.should(s -> s.matchPhrase(m -> m.field("fullName.shingle")
                                        .query(searchTerm)
                                        .boost(phoneLikeSearch ? 2.0f : 12.0f)));

                        b.should(s -> s.match(m -> m.field("fullName")
                                        .query(searchTerm)
                                        .boost(phoneLikeSearch ? 2.0f : 8.0f)));

                        b.should(s -> s.match(m -> m.field("fullName.fuzzy")
                                        .query(searchTerm)
                                        .fuzziness("1")
                                        .prefixLength(0)
                                        .maxExpansions(50)
                                        .fuzzyTranspositions(true)
                                        .boost(phoneLikeSearch ? 0.4f : 1.0f)));

                        b.should(s -> s.multiMatch(mm -> mm.fields("fullName", "fullName.fuzzy")
                                        .query(searchTerm)
                                        .fuzziness("1")
                                        .prefixLength(0)
                                        .maxExpansions(50)
                                        .type(TextQueryType.BestFields)
                                        .boost(phoneLikeSearch ? 0.3f : 0.8f)));

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

                long esStartedAt = System.nanoTime();
                SearchHits<UserIndex> hits = esOps.search(
                                nativeQuery,
                                UserIndex.class,
                                IndexCoordinates.of(esProperties.getUserAlias()));
                long esLatencyMs = elapsedMs(esStartedAt);

                ContextFetchResult contextFetchResult = fetchUserSearchContext(hits.getSearchHits());
                RecentInteractionFetchResult recentInteractionFetchResult = fetchRecentInteractionScores(
                                currentUserId,
                                extractUserIds(hits.getSearchHits()));
                List<RankedUserSearchHit> rankedHits = rerankHits(
                                hits.getSearchHits(),
                                searchTerm,
                                currentUserId,
                                contextFetchResult.contextByUserId(),
                                recentInteractionFetchResult.scoreByUserId());

                List<RankedUserSearchHit> pagedHits = paginateRankedHits(rankedHits, pageable);

                log.info(
                                "User search completed keywordLength={}, phoneSearch={}, candidateSize={}, esTotalHits={}, esReturnedHits={}, esLatencyMs={}, contextFetchSize={}, contextFetchLatencyMs={}, recentInteractionFetchSize={}, recentInteractionLatencyMs={}, returned={}, contextFallback={}, recentInteractionFallback={}, tookMs={}",
                                searchTerm.length(),
                                phoneLikeSearch,
                                candidateSize,
                                hits.getTotalHits(),
                                hits.getSearchHits().size(),
                                esLatencyMs,
                                contextFetchResult.contextFetchSize(),
                                contextFetchResult.latencyMs(),
                                recentInteractionFetchResult.fetchSize(),
                                recentInteractionFetchResult.latencyMs(),
                                pagedHits.size(),
                                contextFetchResult.fallback(),
                                recentInteractionFetchResult.fallback(),
                                elapsedMs(startedAt));

                return PageResponse.<List<RankedUserSearchHit>>builder()
                                .data(pagedHits)
                                .page(pageable.getPageNumber())
                                .totalPages(calculateTotalPages(rankedHits.size(), pageable.getPageSize()))
                                .limit(pageable.getPageSize())
                                .totalItems(rankedHits.size())
                                .build();
        }

        private UserSearchResponse toUserSearchResponse(RankedUserSearchHit hit, String searchTerm, boolean debug) {
                UserIndex userIndex = hit.userIndex();
                UserSearchContextResponse context = hit.context();

                return UserSearchResponse.builder()
                                .id(userIndex.getId())
                                .fullName(userIndex.getFullName())
                                .avatar(resolveAvatar(userIndex))
                                .phoneNumber(shouldExposePhone(searchTerm) ? userIndex.getPhoneNumber() : null)
                                .friendshipId(context != null ? context.friendshipId() : null)
                                .friendshipStatus(context != null ? context.friendshipStatus() : null)
                                .requestedBy(context != null ? context.requestedBy() : null)
                                .relationshipLabel(resolveRelationshipLabel(hit.relationshipLabel()))
                                .mutualFriendsCount(context != null && context.mutualFriendsCount() != null ? context.mutualFriendsCount() : 0)
                                .sharedGroupsCount(context != null && context.sharedGroupsCount() != null ? context.sharedGroupsCount() : 0)
                                .inContact(context != null && Boolean.TRUE.equals(context.inContact()))
                                .debug(debug ? hit.scoreBreakdown() : null)
                                .build();
        }

        private UserSummaryResponse toUserSummaryResponse(UserIndex userIndex, String searchTerm) {
                return UserSummaryResponse.builder()
                                .id(userIndex.getId())
                                .fullName(userIndex.getFullName())
                                .avatar(resolveAvatar(userIndex))
                                .phoneNumber(shouldExposePhone(searchTerm) ? userIndex.getPhoneNumber() : null)
                                .build();
        }

        private String resolveAvatar(UserIndex userIndex) {
                String baseUrl = s3UtilV2.getS3BaseUrl();
                return userIndex.getAvatar() != null ? baseUrl + userIndex.getAvatar() : null;
        }

        private boolean shouldExposePhone(String searchTerm) {
                return PhoneUtil.isValidVnPhone(searchTerm);
        }

        private String normalizePhoneSearchTerm(String searchTerm) {
                return PhoneUtil.normalizeVnPhone(searchTerm)
                                .orElseGet(() -> searchTerm.replaceAll("\\D", ""));
        }

        private boolean isPhoneLike(String searchTerm) {
                return StringUtils.hasText(searchTerm) && searchTerm.replaceAll("\\D", "").length() >= 3;
        }

        private String resolveRelationshipLabel(UserSearchRelationshipLabel relationshipLabel) {
                if (relationshipLabel == null) {
                        return null;
                }

                return localizationUtil.getMessage(
                                relationshipLabel.messageKey(),
                                relationshipLabel.args().toArray());
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
                int candidateSize = Math.max(requestedEnd * RERANK_WINDOW_MULTIPLIER, MIN_RERANK_CANDIDATE_SIZE);
                return Math.min(candidateSize, MAX_RERANK_CANDIDATE_SIZE);
        }

        private Pageable toPageable(int page, int size) {
                return PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        }

        private Pageable normalizePageable(Pageable pageable) {
                if (pageable == null || pageable.isUnpaged()) {
                        return PageRequest.of(0, 20);
                }

                return PageRequest.of(
                                Math.max(pageable.getPageNumber(), 0),
                                Math.min(Math.max(pageable.getPageSize(), 1), MAX_USER_SEARCH_PAGE_SIZE),
                                pageable.getSort());
        }

        private List<RankedUserSearchHit> rerankHits(
                        List<SearchHit<UserIndex>> searchHits,
                        String searchTerm,
                        String currentUserId,
                        Map<String, UserSearchContextResponse> contextByUserId,
                        Map<String, Double> recentInteractionScoreByUserId) {

                if (searchHits == null || searchHits.isEmpty()) {
                        return Collections.emptyList();
                }

                return searchHits.stream()
                                .map(hit -> toRankedHit(hit, searchTerm, currentUserId, contextByUserId, recentInteractionScoreByUserId))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .sorted(Comparator
                                                .comparingDouble(RankedUserSearchHit::finalScore).reversed()
                                                .thenComparing(Comparator.comparingDouble(RankedUserSearchHit::elasticsearchScore).reversed())
                                                .thenComparing(RankedUserSearchHit::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                                .toList();
        }

        private ContextFetchResult fetchUserSearchContext(List<SearchHit<UserIndex>> searchHits) {
                long startedAt = System.nanoTime();
                List<String> userIds = extractUserIds(searchHits);

                if (userIds.isEmpty()) {
                        return new ContextFetchResult(Collections.emptyMap(), 0, elapsedMs(startedAt), false);
                }

                try {
                        ApiResponse<List<UserSearchContextResponse>> response = friendServiceClient.getUserSearchContext(
                                        UserSearchContextRequest.builder()
                                                        .targetUserIds(userIds)
                                                        .build());

                        if (response == null || response.data() == null) {
                                return new ContextFetchResult(Collections.emptyMap(), userIds.size(), elapsedMs(startedAt), false);
                        }

                        Map<String, UserSearchContextResponse> contextByUserId = response.data().stream()
                                        .filter(context -> StringUtils.hasText(context.userId()))
                                        .collect(Collectors.toMap(
                                                        UserSearchContextResponse::userId,
                                                        Function.identity(),
                                                        (existing, replacement) -> existing));
                        return new ContextFetchResult(contextByUserId, userIds.size(), elapsedMs(startedAt), false);
                } catch (Exception e) {
                        long latencyMs = elapsedMs(startedAt);
                        log.warn("User search friend context fallback targetCount={}, latencyMs={}, reason={}",
                                        userIds.size(), latencyMs, e.getMessage());
                        return new ContextFetchResult(Collections.emptyMap(), userIds.size(), latencyMs, true);
                }
        }

        private RecentInteractionFetchResult fetchRecentInteractionScores(String currentUserId, List<String> userIds) {
                long startedAt = System.nanoTime();
                if (!StringUtils.hasText(currentUserId) || userIds == null || userIds.isEmpty()) {
                        return new RecentInteractionFetchResult(Collections.emptyMap(), 0, elapsedMs(startedAt), false);
                }

                try {
                        Map<String, Double> scores = userInteractionFeatureRepository
                                        .findByUserIdAndTargetUserIdIn(currentUserId, userIds)
                                        .stream()
                                        .filter(feature -> StringUtils.hasText(feature.getTargetUserId()))
                                        .collect(Collectors.toMap(
                                                        UserInteractionFeature::getTargetUserId,
                                                        UserInteractionFeature::getRecentInteractionScore,
                                                        Math::max));
                        return new RecentInteractionFetchResult(scores, userIds.size(), elapsedMs(startedAt), false);
                } catch (Exception e) {
                        long latencyMs = elapsedMs(startedAt);
                        log.warn("User search precomputed recent interaction fallback targetCount={}, latencyMs={}, reason={}",
                                        userIds.size(), latencyMs, e.getMessage());
                        return new RecentInteractionFetchResult(Collections.emptyMap(), userIds.size(), latencyMs, true);
                }
        }

        private List<String> extractUserIds(List<SearchHit<UserIndex>> searchHits) {
                if (searchHits == null || searchHits.isEmpty()) {
                        return Collections.emptyList();
                }

                return searchHits.stream()
                                .map(SearchHit::getContent)
                                .map(UserIndex::getId)
                                .filter(StringUtils::hasText)
                                .distinct()
                                .toList();
        }

        private long elapsedMs(long startedAt) {
                return (System.nanoTime() - startedAt) / 1_000_000;
        }

        private Optional<RankedUserSearchHit> toRankedHit(
                        SearchHit<UserIndex> hit,
                        String searchTerm,
                        String currentUserId,
                        Map<String, UserSearchContextResponse> contextByUserId,
                        Map<String, Double> recentInteractionScoreByUserId) {

                UserIndex userIndex = hit.getContent();
                UserSearchContextResponse context = contextByUserId.get(userIndex.getId());

                if (context != null && (Boolean.TRUE.equals(context.blockedByMe()) || Boolean.TRUE.equals(context.blockedMe()))) {
                        return Optional.empty();
                }

                UserSearchRankingContext rankingContext = toRankingContext(
                                searchTerm,
                                userIndex,
                                context,
                                recentInteractionScoreByUserId.getOrDefault(userIndex.getId(), 0.0));
                UserSearchScoreBreakdown scoreBreakdown = rankingStrategy.calculateScoreBreakdown(
                                hit.getScore(),
                                rankingContext,
                                currentUserId);
                UserSearchRelationshipLabel relationshipLabel = rankingStrategy.resolveRelationshipLabel(
                                rankingContext,
                                currentUserId);

                return Optional.of(new RankedUserSearchHit(
                                userIndex,
                                context,
                                relationshipLabel,
                                hit.getScore(),
                                scoreBreakdown,
                                scoreBreakdown.finalScore(),
                                userIndex.getCreatedAt()));
        }

        private UserSearchRankingContext toRankingContext(
                        String searchTerm,
                        UserIndex userIndex,
                        UserSearchContextResponse context,
                        double recentInteractionScore) {

                return new UserSearchRankingContext(
                                context != null ? context.friendshipStatus() : null,
                                context != null ? context.requestedBy() : null,
                                context != null && context.mutualFriendsCount() != null ? context.mutualFriendsCount() : 0,
                                context != null && context.sharedGroupsCount() != null ? context.sharedGroupsCount() : 0,
                                context != null && Boolean.TRUE.equals(context.inContact()),
                                context != null && context.contactScore() != null ? context.contactScore() : 0.0,
                                rankingStrategy.isExactPhoneMatch(searchTerm, userIndex.getPhoneNumber()),
                                recentInteractionScore);
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

        private void ensureDebugAllowed() {
                if (securityUtil.hasRole(Role.ADMIN.name()) || isNonProductionProfile()) {
                        return;
                }

                throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        private boolean isNonProductionProfile() {
                for (String profile : environment.getActiveProfiles()) {
                        if ("dev".equalsIgnoreCase(profile)
                                        || "local".equalsIgnoreCase(profile)
                                        || "test".equalsIgnoreCase(profile)) {
                                return true;
                        }
                }
                return false;
        }

        private record RankedUserSearchHit(
                UserIndex userIndex,
                UserSearchContextResponse context,
                UserSearchRelationshipLabel relationshipLabel,
                double elasticsearchScore,
                UserSearchScoreBreakdown scoreBreakdown,
                double finalScore,
                LocalDateTime createdAt) {
        }

        private record ContextFetchResult(
                Map<String, UserSearchContextResponse> contextByUserId,
                int contextFetchSize,
                long latencyMs,
                boolean fallback) {
        }

        private record RecentInteractionFetchResult(
                Map<String, Double> scoreByUserId,
                int fetchSize,
                long latencyMs,
                boolean fallback) {
        }
}
