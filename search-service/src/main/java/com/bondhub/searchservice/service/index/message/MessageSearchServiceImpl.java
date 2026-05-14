package com.bondhub.searchservice.service.index.message;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.NamedValue;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.PhoneUtil;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.searchservice.client.ConversationMemberClient;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.request.MessageSearchRequest;
import com.bondhub.searchservice.dto.response.MessageNavigationResponse;
import com.bondhub.searchservice.dto.response.MessageSearchGroupResponse;
import com.bondhub.searchservice.dto.response.MessageSearchResponse;
import com.bondhub.searchservice.enums.MessageSearchSection;
import com.bondhub.searchservice.model.elasticsearch.MessageIndex;
import com.bondhub.searchservice.service.index.user.UserSearchService;
import feign.FeignException;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageSearchServiceImpl implements MessageSearchService {

    private static final String FILE_MESSAGE_TYPE = "FILE";
    private static final String LINK_MESSAGE_TYPE = "LINK";
    private static final String LINK_PREFIX = "[Link]";
    private static final String LINK_INVITE_TEXT = "Bấm vào đây để tham gia nhóm trên Bondhub";
    private static final List<String> LINK_INVITE_TEXT_VARIANTS = List.of(
            LINK_INVITE_TEXT,
            "Bấm vào đây để tham gia nhóm trên Zalo zalo.me"
    );
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    ElasticsearchOperations esOperations;
    ElasticsearchProperties esProperties;
    ConversationMemberClient conversationMemberClient;
    UserSearchService userSearchService;
    S3UtilV2 s3UtilV2;

    @Override
    public PageResponse<List<ConversationSearchResponse>> searchConversations(
            String userId,
            String keyword,
            Boolean isGroup,
            Pageable pageable) {

        // 1. Get existing conversations (contacts you've chatted with)
        ApiResponse<PageResponse<List<ConversationSearchResponse>>> response =
                conversationMemberClient.searchConversations(
                        userId,
                        keyword,
                        isGroup,
                        pageable.getPageNumber(),
                        pageable.getPageSize());

        PageResponse<List<ConversationSearchResponse>> conversationPage =
                response != null && response.data() != null
                        ? sanitizeConversationPhones(response.data(), keyword, pageable)
                        : PageResponse.empty(pageable);

        // 2. If it's not a group-only search and keyword exists, also search global users
        if (!Boolean.TRUE.equals(isGroup) && hasText(keyword)) {
            String searchTerm = PhoneUtil.normalizeVnPhone(keyword).orElse(keyword);
            PageResponse<List<UserSummaryResponse>> userPage = userSearchService.searchUsers(searchTerm, pageable);

            if (userPage != null && userPage.data() != null && !userPage.data().isEmpty()) {
                List<ConversationSearchResponse> mergedResults = new ArrayList<>(conversationPage.data());

                // Avoid duplicates: don't add users who already have a conversation in the results
                List<String> existingRecipientIds = mergedResults.stream()
                        .map(ConversationSearchResponse::recipientId)
                        .filter(Objects::nonNull)
                        .toList();

                for (UserSummaryResponse user : userPage.data()) {
                    if (!existingRecipientIds.contains(user.id())) {
                        mergedResults.add(ConversationSearchResponse.builder()
                                .conversationId(null) // No conversation yet
                                .recipientId(user.id())
                                .name(user.fullName())
                                .avatar(user.avatar())
                                .group(false)
                                .memberCount(0)
                                .displayHighlights(user.fullName())
                                .phoneNumber(user.phoneNumber())
                                .build());
                    }
                }

                // Update the total items and data
                long totalItems = Math.max(conversationPage.totalItems(), mergedResults.size());
                return PageResponse.<List<ConversationSearchResponse>>builder()
                        .data(mergedResults)
                        .page(conversationPage.page())
                        .limit(conversationPage.limit())
                        .totalItems(totalItems)
                        .totalPages(conversationPage.totalPages())
                        .build();
            }
        }

        return conversationPage;
    }

    private PageResponse<List<ConversationSearchResponse>> sanitizeConversationPhones(
            PageResponse<List<ConversationSearchResponse>> page,
            String keyword,
            Pageable pageable) {
        if (page == null || page.data() == null || shouldExposePhone(keyword)) {
            return page;
        }

        return PageResponse.<List<ConversationSearchResponse>>builder()
                .data(page.data().stream()
                        .map(this::withoutPhoneNumber)
                        .toList())
                .page(page.page())
                .limit(page.limit())
                .totalItems(page.totalItems())
                .totalPages(page.totalPages())
                .build();
    }

    private ConversationSearchResponse withoutPhoneNumber(ConversationSearchResponse response) {
        if (response == null || response.phoneNumber() == null) {
            return response;
        }

        return ConversationSearchResponse.builder()
                .conversationId(response.conversationId())
                .recipientId(response.recipientId())
                .name(response.name())
                .avatar(response.avatar())
                .group(response.group())
                .memberCount(response.memberCount())
                .participantNames(response.participantNames())
                .participantAvatars(response.participantAvatars())
                .displayHighlights(response.displayHighlights())
                .phoneNumber(null)
                .build();
    }

    private boolean shouldExposePhone(String keyword) {
        return PhoneUtil.isValidVnPhone(keyword);
    }

    @Override
    public PageResponse<List<MessageSearchResponse>> searchMessages(
            String userId,
            MessageSearchRequest request,
            MessageSearchSection section,
            Pageable pageable) {
        ConversationMemberLookupResponse membership = resolveConversationMembership(request.conversationId(), userId);

        NativeQuery query = buildQuery(
                userId,
                request,
                membership != null ? membership.joinedAt() : null,
                section,
                pageable);

        SearchHits<MessageIndex> hits = esOperations.search(
                query,
                MessageIndex.class,
                IndexCoordinates.of(esProperties.getMessageAlias())
        );

        SearchPage<MessageIndex> page = SearchHitSupport.searchPageFor(hits, pageable);
        return PageResponse.fromPage(page, hit -> this.toResponse(hit, request.keyword()));
    }

    @Override
    public List<ConversationSearchResponse> searchMessageSenders(String userId, String keyword) {
        if (!hasText(keyword)) {
            return List.of();
        }

        log.info("searchMessageSenders called with userId={}, keyword={}", userId, keyword);

        Query query = Query.of(q -> q.bool(b -> {
            b.must(m -> m.matchPhrasePrefix(mm -> mm
                    .field("searchableText")
                    .query(keyword)
            ));

            b.filter(f -> f.term(t -> t
                    .field("participantIds")
                    .value(userId)
            ));

            b.filter(f -> f.terms(t -> t
                    .field("type")
                    .terms(values -> values.value(List.of(
                            co.elastic.clients.elasticsearch._types.FieldValue.of("CHAT"),
                            co.elastic.clients.elasticsearch._types.FieldValue.of(LINK_MESSAGE_TYPE)
                    )))
            ));

            b.filter(f -> f.bool(vb -> {
                vb.should(s -> s.bool(nb -> nb.mustNot(mn -> mn.exists(e -> e.field("visibleTo")))));
                vb.should(s -> s.term(t -> t.field("visibleTo").value(userId)));
                vb.minimumShouldMatch("1");
                return vb;
            }));

            b.mustNot(mn -> mn.term(t -> t.field("deletedBy").value(userId)));
            b.mustNot(mn -> mn.term(t -> t
                    .field("status")
                    .value(MessageStatus.DELETED_BY_ADMIN.name())
            ));

            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(0)
                .withAggregation("senders", Aggregation.of(a -> a
                        .terms(t -> t.field("senderId").size(50))
                        .aggregations("top_sender_hit", Aggregation.of(sub -> sub
                                .topHits(th -> th.size(1))
                        ))
                ))
                .build();

        SearchHits<MessageIndex> searchHits = esOperations.search(nativeQuery, MessageIndex.class, IndexCoordinates.of(esProperties.getMessageAlias()));

        log.info("searchMessageSenders totalHits={}, hasAggregations={}", searchHits.getTotalHits(), searchHits.hasAggregations());

        if (!searchHits.hasAggregations()) {
            return List.of();
        }

        ElasticsearchAggregations aggregationsContainer = (ElasticsearchAggregations) Objects.requireNonNull(searchHits.getAggregations());
        ElasticsearchAggregation sendersAggregation = aggregationsContainer.get("senders");

        if (sendersAggregation == null) {
            log.warn("searchMessageSenders: 'senders' aggregation is null");
            return List.of();
        }

        List<StringTermsBucket> buckets = sendersAggregation.aggregation().getAggregate().sterms().buckets().array();
        log.info("searchMessageSenders: found {} sender buckets", buckets.size());

        return buckets.stream()
                .map(bucket -> {
                    String senderId = bucket.key().stringValue();
                    log.debug("Processing sender bucket: senderId={}, docCount={}", senderId, bucket.docCount());

                    var topHits = bucket.aggregations().get("top_sender_hit").topHits();
                    if (topHits.hits().hits().isEmpty()) {
                        log.debug("No top hits for senderId={}", senderId);
                        return null;
                    }

                    var hit = topHits.hits().hits().get(0);

                    if (hit.source() == null) {
                        log.debug("No _source for senderId={}", senderId);
                        return null;
                    }

                    String senderName = null;
                    String senderAvatar = null;
                    try {
                        var sourceJson = hit.source().toJson().asJsonObject();
                        senderName = sourceJson.containsKey("senderName") && !sourceJson.isNull("senderName")
                                ? sourceJson.getString("senderName") : null;
                        senderAvatar = sourceJson.containsKey("senderAvatar") && !sourceJson.isNull("senderAvatar")
                                ? sourceJson.getString("senderAvatar") : null;
                    } catch (Exception e) {
                        log.error("Error extracting sender fields for senderId={}: {}", senderId, e.getMessage());
                    }

                    if (senderName == null) {
                        log.warn("Could not resolve senderName for senderId={}, skipping", senderId);
                        return null;
                    }

                    log.debug("Resolved sender: id={}, name={}", senderId, senderName);

                    return ConversationSearchResponse.builder()
                            .recipientId(senderId)
                            .name(senderName)
                            .avatar(s3UtilV2.getFullUrl(senderAvatar))
                            .group(false)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public PageResponse<List<MessageSearchGroupResponse>> searchMessageGroups(
            String userId,
            MessageSearchRequest request,
            Pageable pageable) {
        if (!hasText(request.keyword())) {
            return PageResponse.empty(pageable);
        }

        NativeQuery nativeQuery = buildGroupQuery(userId, request, pageable);
        SearchHits<MessageIndex> searchHits = esOperations.search(
                nativeQuery,
                MessageIndex.class,
                IndexCoordinates.of(esProperties.getMessageAlias())
        );

        if (!searchHits.hasAggregations()) {
            return PageResponse.empty(pageable);
        }

        ElasticsearchAggregations aggregationsContainer =
                (ElasticsearchAggregations) Objects.requireNonNull(searchHits.getAggregations());
        ElasticsearchAggregation conversationsAggregation = aggregationsContainer.get("conversations");

        if (conversationsAggregation == null) {
            return PageResponse.empty(pageable);
        }

        List<StringTermsBucket> buckets = conversationsAggregation.aggregation()
                .getAggregate()
                .sterms()
                .buckets()
                .array();

        int offset = (int) pageable.getOffset();
        int pageSize = pageable.getPageSize();
        List<MessageSearchGroupResponse> groups = buckets.stream()
                .skip(offset)
                .limit(pageSize)
                .map(bucket -> toGroupResponse(bucket, request.keyword(), userId))
                .filter(Objects::nonNull)
                .toList();

        long totalItems = resolveCardinalityTotal(aggregationsContainer, buckets.size());
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 0;

        return PageResponse.<List<MessageSearchGroupResponse>>builder()
                .data(groups)
                .page(pageable.getPageNumber())
                .limit(pageSize)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }

    private NativeQuery buildGroupQuery(String userId, MessageSearchRequest request, Pageable pageable) {
        NativeQuery baseQuery = buildQuery(userId, request, null, MessageSearchSection.ALL, Pageable.unpaged());
        int aggregationSize = Math.max(pageable.getPageSize(), (int) pageable.getOffset() + pageable.getPageSize());

        return NativeQuery.builder()
                .withQuery(baseQuery.getQuery())
                .withMaxResults(0)
                .withAggregation("conversation_count", Aggregation.of(a -> a
                        .cardinality(c -> c.field("conversationId"))
                ))
                .withAggregation("conversations", Aggregation.of(a -> a
                        .terms(t -> t
                                .field("conversationId")
                                .size(aggregationSize)
                                .order(List.of(NamedValue.of(
                                        "last_matched_at",
                                        SortOrder.Desc
                                )))
                        )
                        .aggregations("last_matched_at", Aggregation.of(sub -> sub
                                .max(m -> m.field("createdAt"))
                        ))
                        .aggregations("latest_match", Aggregation.of(sub -> sub
                                .topHits(th -> th
                                        .size(1)
                                        .sort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                                )
                        ))
                ))
                .build();
    }

    private long resolveCardinalityTotal(ElasticsearchAggregations aggregationsContainer, int fallback) {
        ElasticsearchAggregation countAggregation = aggregationsContainer.get("conversation_count");
        if (countAggregation == null) {
            return fallback;
        }

        Aggregate aggregate = countAggregation.aggregation().getAggregate();
        return aggregate.isCardinality() ? aggregate.cardinality().value() : fallback;
    }

    private MessageSearchGroupResponse toGroupResponse(StringTermsBucket bucket, String keyword, String userId) {
        var topHits = bucket.aggregations().get("latest_match").topHits();
        if (topHits.hits().hits().isEmpty() || topHits.hits().hits().get(0).source() == null) {
            return null;
        }

        JsonObject source = topHits.hits().hits().get(0).source().toJson().asJsonObject();
        String type = getString(source, "type");
        String displayContent = resolveDisplayContent(source);
        String displayHighlights = highlightDisplayContent(displayContent, keyword);
        boolean isGroup = getBoolean(source, "group");
        List<String> participantNames = getStringList(source, "participantNames");
        List<String> participantAvatars = getStringList(source, "participantAvatars").stream()
                .map(s3UtilV2::getFullUrl)
                .toList();
        int peerIndex = resolvePeerParticipantIndex(source, userId);

        String title = isGroup
                ? firstNonBlank(
                        getString(source, "conversationName"),
                        getString(source, "conversationSearchText"),
                        joinParticipantNames(participantNames))
                : firstNonBlank(
                        getValueAt(participantNames, peerIndex),
                        getString(source, "conversationName"),
                        getString(source, "senderName"));
        String avatar = isGroup
                ? getString(source, "conversationAvatar")
                : firstNonBlank(
                        getValueAt(participantAvatars, peerIndex),
                        getString(source, "conversationAvatar"),
                        getString(source, "senderAvatar"));

        return MessageSearchGroupResponse.builder()
                .messageId(getString(source, "id"))
                .conversationId(bucket.key().stringValue())
                .title(title)
                .avatar(s3UtilV2.getFullUrl(avatar))
                .isGroup(isGroup)
                .matchCount(bucket.docCount())
                .previewContent(displayContent)
                .previewHighlights(displayHighlights)
                .previewType(type)
                .size(getLong(source, "size"))
                .hasAttachment(getBoolean(source, "hasAttachment"))
                .hasLink(getBoolean(source, "hasLink"))
                .lastMatchedAt(getInstant(source, "createdAt"))
                .participantNames(participantNames)
                .participantAvatars(participantAvatars)
                .build();
    }

    private int resolvePeerParticipantIndex(JsonObject source, String userId) {
        List<String> participantIds = getStringList(source, "participantIds");
        for (int index = 0; index < participantIds.size(); index++) {
            if (!Objects.equals(participantIds.get(index), userId)) {
                return index;
            }
        }
        return -1;
    }

    private String getValueAt(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private String joinParticipantNames(List<String> participantNames) {
        if (participantNames == null || participantNames.isEmpty()) {
            return null;
        }
        return String.join(", ", participantNames);
    }

    private String resolveDisplayContent(JsonObject source) {
        String type = getString(source, "type");
        if (FILE_MESSAGE_TYPE.equalsIgnoreCase(type)) {
            return firstNonBlank(getString(source, "originalFileName"), getString(source, "content"));
        }

        boolean hasLink = getBoolean(source, "hasLink");
        if (LINK_MESSAGE_TYPE.equalsIgnoreCase(type) || hasLink) {
            String groupName = getString(source, "linkGroupName");
            String linkUrl = firstNonBlank(getString(source, "linkUrl"), extractFirstUrl(getString(source, "searchableText")));
            String content = getString(source, "content");

            if (!hasText(groupName) && !hasText(linkUrl)) {
                return firstNonBlank(getString(source, "searchableText"), content);
            }

            List<String> parts = new ArrayList<>();
            if (hasText(content)) {
                parts.add(content);
            }
            if (hasText(groupName)) {
                parts.add(groupName);
            }
            if (hasText(linkUrl)) {
                parts.add(linkUrl);
            }
            return String.join(" ", parts);
        }

        return getString(source, "content");
    }

    private String getString(JsonObject source, String field) {
        return source.containsKey(field) && !source.isNull(field)
                ? source.getString(field, null)
                : null;
    }

    private boolean getBoolean(JsonObject source, String field) {
        return source.containsKey(field) && !source.isNull(field) && source.getBoolean(field, false);
    }

    private Long getLong(JsonObject source, String field) {
        if (!source.containsKey(field) || source.isNull(field)) {
            return null;
        }
        JsonValue value = source.get(field);
        if (value instanceof JsonNumber number) {
            return number.longValue();
        }
        if (value instanceof JsonString string) {
            String text = string.getString();
            return hasText(text) && text.trim().matches("\\d+") ? Long.parseLong(text.trim()) : null;
        }
        return null;
    }

    private Instant getInstant(JsonObject source, String field) {
        if (!source.containsKey(field) || source.isNull(field)) {
            return null;
        }
        JsonValue value = source.get(field);
        if (value instanceof JsonNumber number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (!(value instanceof JsonString)) {
            return null;
        }

        String text = getString(source, field);
        if (!hasText(text)) {
            return null;
        }

        String trimmed = text.trim();
        if (trimmed.matches("\\d+")) {
            return Instant.ofEpochMilli(Long.parseLong(trimmed));
        }

        return Instant.parse(trimmed);
    }

    private List<String> getStringList(JsonObject source, String field) {
        if (!source.containsKey(field) || source.isNull(field)) {
            return List.of();
        }

        JsonArray array = source.getJsonArray(field);
        if (array == null) {
            return List.of();
        }

        return array.stream()
                .filter(value -> value.getValueType() == JsonValue.ValueType.STRING)
                .map(value -> {
                    String text = ((JsonString) value).getString();
                    return hasText(text) ? text : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private ConversationMemberLookupResponse resolveConversationMembership(String conversationId, String userId) {
        if (!hasText(conversationId)) {
            return null;
        }

        return getConversationMembership(conversationId, userId);
    }

    private ConversationMemberLookupResponse getConversationMembership(String conversationId, String userId) {
        try {
            ApiResponse<ConversationMemberLookupResponse> membershipResponse =
                    conversationMemberClient.getConversationMember(conversationId, userId);

            ConversationMemberLookupResponse membership =
                    membershipResponse != null ? membershipResponse.data() : null;

            if (membership == null || !membership.member()) {
                throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
            }

            return membership;
        } catch (FeignException.Forbidden | FeignException.NotFound exception) {
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }
    }

    private NativeQuery buildQuery(
            String userId,
            MessageSearchRequest request,
            Instant joinedAt,
            MessageSearchSection section,
            Pageable pageable) {

        Instant lowerBound = resolveLowerBound(request, joinedAt);
        Instant upperBound = request.to() != null ? Instant.ofEpochMilli(request.to()) : null;
        boolean hasKeyword = hasText(request.keyword());
        List<String> requestedTypeFilters = resolveRequestedTypeFilters(request.filters());

        if (lowerBound != null && upperBound != null && lowerBound.isAfter(upperBound)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Query query = Query.of(q -> q.bool(b -> {
            if (hasKeyword) {
                b.must(m -> m.matchPhrasePrefix(mm -> mm
                        .field("searchableText")
                        .query(request.keyword())
                ));
            }

            if (hasText(request.conversationId())) {
                b.filter(f -> f.term(t -> t
                        .field("conversationId")
                        .value(request.conversationId())
                ));
            } else {
                b.filter(f -> f.term(t -> t
                        .field("participantIds")
                        .value(userId)
                ));
            }

            if (request.senderId() != null) {
                b.filter(f -> f.term(t -> t
                        .field("senderId")
                        .value(request.senderId())
                ));
            }

            if (!requestedTypeFilters.isEmpty()) {
                b.filter(f -> f.terms(t -> t
                        .field("type")
                        .terms(values -> values.value(requestedTypeFilters.stream()
                                .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                .toList()))
                ));
            } else if (section == MessageSearchSection.FILES) {
                b.filter(f -> f.term(t -> t
                        .field("type")
                        .value(FILE_MESSAGE_TYPE)
                ));

                if (request.fileType() != null) {
                    String ft = request.fileType().toUpperCase(Locale.ROOT);
                    b.filter(ff -> {
                        switch (ft) {
                            case "PDF" -> ff.term(t -> t.field("fileExtension").value("pdf"));
                            case "WORD" -> ff.terms(t -> t.field("fileExtension").terms(v -> v.value(List.of(
                                    co.elastic.clients.elasticsearch._types.FieldValue.of("doc"),
                                    co.elastic.clients.elasticsearch._types.FieldValue.of("docx")
                            ))));
                            case "EXCEL" -> ff.terms(t -> t.field("fileExtension").terms(v -> v.value(List.of(
                                    co.elastic.clients.elasticsearch._types.FieldValue.of("xls"),
                                    co.elastic.clients.elasticsearch._types.FieldValue.of("xlsx")
                            ))));
                            case "POWERPOINT" -> ff.terms(t -> t.field("fileExtension").terms(v -> v.value(List.of(
                                    co.elastic.clients.elasticsearch._types.FieldValue.of("ppt"),
                                    co.elastic.clients.elasticsearch._types.FieldValue.of("pptx")
                            ))));
                        }
                        return ff;
                    });
                }
            } else if (section == MessageSearchSection.MESSAGES) {
                b.filter(f -> f.terms(t -> t
                        .field("type")
                        .terms(values -> values.value(List.of(
                                co.elastic.clients.elasticsearch._types.FieldValue.of("CHAT"),
                                co.elastic.clients.elasticsearch._types.FieldValue.of(LINK_MESSAGE_TYPE)
                        )))
                ));
            } else {
                b.filter(f -> f.terms(t -> t
                        .field("type")
                        .terms(values -> values.value(List.of(
                                co.elastic.clients.elasticsearch._types.FieldValue.of("CHAT"),
                                co.elastic.clients.elasticsearch._types.FieldValue.of(FILE_MESSAGE_TYPE),
                                co.elastic.clients.elasticsearch._types.FieldValue.of(LINK_MESSAGE_TYPE)
                        )))
                ));
            }

            if (lowerBound != null || upperBound != null) {
                b.filter(f -> f.range(r -> r.date(d -> {
                    d.field("createdAt");
                    d.format("epoch_millis");
                    if (lowerBound != null) {
                        d.gte(Long.toString(lowerBound.toEpochMilli()));
                    }
                    if (upperBound != null) {
                        d.lte(Long.toString(upperBound.toEpochMilli()));
                    }
                    return d;
                })));
            }

            b.filter(f -> f.bool(vb -> {
                vb.should(s -> s.bool(nb -> nb.mustNot(mn -> mn.exists(e -> e.field("visibleTo")))));
                vb.should(s -> s.term(t -> t.field("visibleTo").value(userId)));
                vb.minimumShouldMatch("1");
                return vb;
            }));

            b.mustNot(mn -> mn.term(t -> t.field("deletedBy").value(userId)));
            b.mustNot(mn -> mn.term(t -> t
                    .field("status")
                    .value(MessageStatus.DELETED_BY_ADMIN.name())
            ));

            return b;
        }));

        return NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .withSort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                .withHighlightQuery(buildHighlightQuery())
                .build();
    }

    private PageResponse<List<MessageSearchResponse>> executeSearch(
            String userId,
            MessageSearchRequest request,
            Instant joinedAt,
            MessageSearchSection section,
            Pageable pageable) {
        NativeQuery query = buildQuery(userId, request, joinedAt, section, pageable);

        SearchHits<MessageIndex> hits = esOperations.search(
                query,
                MessageIndex.class,
                IndexCoordinates.of(esProperties.getMessageAlias())
        );

        SearchPage<MessageIndex> page = SearchHitSupport.searchPageFor(hits, pageable);
        return PageResponse.fromPage(page, hit -> this.toResponse(hit, request.keyword()));
    }

    private List<String> resolveRequestedTypeFilters(String filters) {
        if (!hasText(filters)) {
            return List.of();
        }

        List<String> types = new ArrayList<>();
        for (String token : filters.split(",")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if ("link".equals(normalized) && !types.contains(LINK_MESSAGE_TYPE)) {
                types.add(LINK_MESSAGE_TYPE);
            }
            if ("file".equals(normalized) && !types.contains(FILE_MESSAGE_TYPE)) {
                types.add(FILE_MESSAGE_TYPE);
            }
        }
        return types;
    }

    private HighlightQuery buildHighlightQuery() {
        HighlightParameters parameters = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withRequireFieldMatch(false)
                .build();

        HighlightField field = new HighlightField(
                "searchableText",
                HighlightFieldParameters.builder()
                        .withFragmentSize(150)
                        .withNumberOfFragments(1)
                        .build()
        );

        return new HighlightQuery(new Highlight(parameters, List.of(field)), MessageIndex.class);
    }

    private Instant resolveLowerBound(MessageSearchRequest request, Instant joinedAt) {
        List<Instant> lowerBounds = new ArrayList<>();
        if (joinedAt != null) {
            lowerBounds.add(joinedAt);
        }
        if (request.from() != null) {
            lowerBounds.add(Instant.ofEpochMilli(request.from()));
        }

        Instant shorthandBound = resolveDateRange(request.dateRange());
        if (shorthandBound != null) {
            lowerBounds.add(shorthandBound);
        }

        return lowerBounds.stream()
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant resolveDateRange(String dateRange) {
        if (dateRange == null) {
            return null;
        }

        Instant now = Instant.now();
        return switch (dateRange) {
            case "7d" -> now.minus(7, ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            case "3months" -> now.minus(3, ChronoUnit.MONTHS);
            default -> null;
        };
    }

    private MessageSearchResponse toResponse(SearchHit<MessageIndex> hit, String keyword) {
        MessageIndex message = hit.getContent();
        String rawHighlights = resolveHighlight(hit, message, keyword);
        String displayContent = resolveDisplayContent(message);
        String displayHighlights = resolveDisplayHighlights(message, displayContent, rawHighlights, keyword);

        return MessageSearchResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(s3UtilV2.getFullUrl(message.getSenderAvatar()))
                .displayContent(displayContent)
                .size(message.getSize())
                .type(message.getType())
                .status(message.getStatus())
                .hasAttachment(message.isHasAttachment())
                .hasLink(message.isHasLink())
                .isGroup(message.isGroup())
                .conversationName(message.getConversationName())
                .conversationAvatar(s3UtilV2.getFullUrl(message.getConversationAvatar()))
                .participantNames(message.getParticipantNames())
                .participantAvatars(message.getParticipantAvatars() != null
                        ? message.getParticipantAvatars().stream()
                                .map(s3UtilV2::getFullUrl)
                                .toList()
                        : null)
                .createdAt(message.getCreatedAt())
                .displayHighlights(displayHighlights)
                .build();
    }

    private String resolveHighlight(SearchHit<MessageIndex> hit, MessageIndex message, String keyword) {
        List<String> highlightFragments = hit.getHighlightField("searchableText");
        String fragment = (highlightFragments != null && !highlightFragments.isEmpty())
                ? highlightFragments.get(0)
                : null;

        if (fragment == null) {
            String fallback = hasText(message.getSearchableText())
                    ? message.getSearchableText().trim()
                    : hasText(message.getContent()) ? message.getContent().trim() : null;

            if (fallback == null) {
                return null;
            }

            fragment = fallback.length() > 150 ? fallback.substring(0, 150) + "..." : fallback;
        }

        if (hasText(keyword) && fragment.contains("<em>") && fragment.contains("</em>")) {
            try {
                String normalizedKeyword = normalizeForComparison(keyword);
                int startTag = fragment.indexOf("<em>");
                int endTag = fragment.indexOf("</em>");
                String innerTerm = fragment.substring(startTag + 4, endTag);
                String normalizedInner = normalizeForComparison(innerTerm);

                int matchIndex = normalizedInner.indexOf(normalizedKeyword);
                if (matchIndex != -1) {
                    int matchEnd = matchIndex + normalizedKeyword.length();
                    String actualMatch = innerTerm.substring(matchIndex, matchEnd);
                    String restOfInner = innerTerm.substring(matchEnd);

                    return fragment.substring(0, startTag)
                            + innerTerm.substring(0, matchIndex)
                            + "<em>" + actualMatch + "</em>"
                            + restOfInner
                            + fragment.substring(endTag + 5);
                }
            } catch (Exception exception) {
                log.warn("Failed to refine highlight for keyword: {}", keyword, exception);
            }
        }

        return fragment;
    }

    private String normalizeForComparison(String text) {
        if (text == null) {
            return "";
        }

        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private String resolveDisplayContent(MessageIndex message) {
        if (FILE_MESSAGE_TYPE.equalsIgnoreCase(message.getType())) {
            if (hasText(message.getOriginalFileName())) {
                return message.getOriginalFileName().trim();
            }
            return hasText(message.getContent()) ? message.getContent().trim() : null;
        }

        if (LINK_MESSAGE_TYPE.equalsIgnoreCase(message.getType()) || message.isHasLink()) {
            String groupName = resolveLinkGroupName(message);
            String linkUrl = resolveLinkUrl(message);

            if (!hasText(groupName) && !hasText(linkUrl)) {
                if (hasText(message.getSearchableText())) {
                    return message.getSearchableText().trim();
                }
                return hasText(message.getContent()) ? message.getContent().trim() : null;
            }

            List<String> parts = new ArrayList<>();
            parts.add(LINK_PREFIX);
            if (hasText(groupName)) {
                parts.add(groupName);
            }
            parts.add(LINK_INVITE_TEXT);
            if (hasText(linkUrl)) {
                parts.add(linkUrl);
            }

            return String.join(" ", parts);
        }

        return hasText(message.getContent()) ? message.getContent().trim() : null;
    }

    private String resolveLinkGroupName(MessageIndex message) {
        if (hasText(message.getLinkGroupName())) {
            return message.getLinkGroupName().trim();
        }

        if (!hasText(message.getSearchableText())) {
            return null;
        }

        String searchableText = message.getSearchableText().trim();
        int inviteTextIndex = indexOfAnyIgnoreCase(searchableText, LINK_INVITE_TEXT_VARIANTS);
        if (inviteTextIndex < 0) {
            return null;
        }

        String prefixSegment = searchableText.substring(0, inviteTextIndex).trim();
        if (prefixSegment.regionMatches(true, 0, LINK_PREFIX, 0, LINK_PREFIX.length())) {
            prefixSegment = prefixSegment.substring(LINK_PREFIX.length()).trim();
        }

        return hasText(prefixSegment) ? prefixSegment : null;
    }

    private String resolveLinkUrl(MessageIndex message) {
        if (hasText(message.getLinkUrl())) {
            return message.getLinkUrl().trim();
        }

        String searchableUrl = extractFirstUrl(message.getSearchableText());
        if (hasText(searchableUrl)) {
            return searchableUrl;
        }

        return extractFirstUrl(message.getContent());
    }

    private String extractFirstUrl(String text) {
        if (!hasText(text)) {
            return null;
        }

        Matcher matcher = URL_PATTERN.matcher(text.trim());
        return matcher.find() ? matcher.group() : null;
    }

    private int indexOfIgnoreCase(String source, String target) {
        return source.toLowerCase(Locale.ROOT).indexOf(target.toLowerCase(Locale.ROOT));
    }

    private int indexOfAnyIgnoreCase(String source, List<String> targets) {
        for (String target : targets) {
            int index = indexOfIgnoreCase(source, target);
            if (index >= 0) {
                return index;
            }
        }

        return -1;
    }

    private String resolveDisplayHighlights(
            MessageIndex message,
            String displayContent,
            String rawHighlights,
            String keyword) {
        if (LINK_MESSAGE_TYPE.equalsIgnoreCase(message.getType()) || message.isHasLink()) {
            return highlightDisplayContent(displayContent, keyword);
        }

        if (FILE_MESSAGE_TYPE.equalsIgnoreCase(message.getType())) {
            return highlightDisplayContent(displayContent, keyword);
        }

        if (hasText(rawHighlights)) {
            return rawHighlights;
        }

        return highlightDisplayContent(displayContent, keyword);
    }

    private String highlightDisplayContent(String displayContent, String keyword) {
        if (!hasText(displayContent)) {
            return null;
        }

        if (!hasText(keyword)) {
            return displayContent;
        }

        String normalizedKeyword = normalizeForComparison(keyword).trim();
        if (normalizedKeyword.isEmpty()) {
            return displayContent;
        }

        List<int[]> ranges = findHighlightRanges(displayContent, normalizedKeyword);
        if (ranges.isEmpty()) {
            return displayContent;
        }

        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        for (int[] range : ranges) {
            int start = range[0];
            int end = range[1];

            if (cursor < start) {
                builder.append(HtmlUtils.htmlEscape(displayContent.substring(cursor, start)));
            }

            builder.append("<em>")
                    .append(HtmlUtils.htmlEscape(displayContent.substring(start, end)))
                    .append("</em>");
            cursor = end;
        }

        if (cursor < displayContent.length()) {
            builder.append(HtmlUtils.htmlEscape(displayContent.substring(cursor)));
        }

        return builder.toString();
    }

    private List<int[]> findHighlightRanges(String original, String normalizedKeyword) {
        String normalizedOriginal = normalizeForComparison(original);
        List<Integer> normalizedToOriginal = buildNormalizedToOriginalIndex(original);
        List<int[]> ranges = new ArrayList<>();
        int searchStart = 0;

        while (searchStart < normalizedOriginal.length()) {
            int matchIndex = normalizedOriginal.indexOf(normalizedKeyword, searchStart);
            if (matchIndex < 0) {
                break;
            }

            int originalStart = normalizedToOriginal.get(matchIndex);
            int originalEnd = normalizedToOriginal.get(matchIndex + normalizedKeyword.length() - 1) + 1;
            ranges.add(new int[]{originalStart, originalEnd});
            searchStart = matchIndex + normalizedKeyword.length();
        }

        return ranges;
    }

    private List<Integer> buildNormalizedToOriginalIndex(String original) {
        List<Integer> normalizedToOriginal = new ArrayList<>();
        for (int index = 0; index < original.length(); index++) {
            String normalizedChar = normalizeForComparison(String.valueOf(original.charAt(index)));
            for (int charIndex = 0; charIndex < normalizedChar.length(); charIndex++) {
                normalizedToOriginal.add(index);
            }
        }

        return normalizedToOriginal;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public MessageNavigationResponse navigateSearchResult(
            String userId,
            String conversationId,
            String keyword,
            String senderId,
            String currentMessageId,
            String direction,
            MessageSearchSection section) {

        log.info("navigateSearchResult start userId={}, conversationId={}, keyword={}, senderId={}, currentMessageId={}, direction={}, section={}",
                userId, conversationId, keyword, senderId, currentMessageId, direction, section);

        if (!hasText(keyword) && !hasText(senderId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (!hasText(conversationId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        ConversationMemberLookupResponse membership = resolveConversationMembership(conversationId, userId);
        Instant joinedAt = membership != null ? membership.joinedAt() : null;
        MessageSearchRequest baseRequest = new MessageSearchRequest(
                keyword, conversationId, senderId, null, null, null, null, null);
        Query baseQuery = Objects.requireNonNull(
                buildQuery(userId, baseRequest, joinedAt, section, Pageable.unpaged()).getQuery());
        long totalMatches = countNavigationMatches(baseQuery);
        log.info("navigateSearchResult matches total={}, conversationId={}, keyword={}, senderId={}",
                totalMatches, conversationId, keyword, senderId);

        if (totalMatches == 0) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        boolean isCurrent = "CURRENT".equalsIgnoreCase(direction);
        boolean isNext = "NEXT".equalsIgnoreCase(direction);
        SearchHit<MessageIndex> targetHit = null;

        if (hasText(currentMessageId)) {
            MessageIndex currentMessage = esOperations.get(
                    currentMessageId,
                    MessageIndex.class,
                    IndexCoordinates.of(esProperties.getMessageAlias()));

            if (currentMessage != null && currentMessage.getCreatedAt() != null) {
                long currentMillis = currentMessage.getCreatedAt().toEpochMilli();
                if (isCurrent) {
                    targetHit = findNavigationTargetById(userId, conversationId, keyword, senderId, joinedAt, section, currentMessageId);
                } else {
                    targetHit = findNavigationTarget(
                            userId,
                            conversationId,
                            keyword,
                            senderId,
                            joinedAt,
                            section,
                            isNext ? currentMillis + 1 : null,
                            isNext ? null : currentMillis - 1,
                            isNext ? SortOrder.Asc : SortOrder.Desc);
                }
            }
        }

        if (targetHit == null) {
            targetHit = findNavigationTarget(
                    userId,
                    conversationId,
                    keyword,
                    senderId,
                    joinedAt,
                    section,
                    null,
                    null,
                    isNext || isCurrent ? SortOrder.Desc : SortOrder.Asc);
        }

        if (targetHit == null) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        MessageIndex targetMessage = targetHit.getContent();
        log.info("navigateSearchResult target messageId={}, createdAt={}, senderId={}",
                targetMessage.getId(), targetMessage.getCreatedAt(), targetMessage.getSenderId());
        String rawHighlights = resolveHighlight(targetHit, targetMessage, keyword);
        String displayContent = resolveDisplayContent(targetMessage);
        String displayHighlights = resolveDisplayHighlights(targetMessage, displayContent, rawHighlights, keyword);
        int displayIndex = countDisplayIndex(
                userId,
                conversationId,
                keyword,
                senderId,
                joinedAt,
                section,
                targetMessage.getCreatedAt());

        return MessageNavigationResponse.builder()
                .messageId(targetMessage.getId())
                .conversationId(targetMessage.getConversationId())
                .index(displayIndex)
                .total((int) Math.min(totalMatches, Integer.MAX_VALUE))
                .createdAt(targetMessage.getCreatedAt())
                .displayHighlights(displayHighlights)
                .direction(direction != null ? direction.toUpperCase() : "NEXT")
                .build();
    }

    private SearchHit<MessageIndex> findNavigationTargetById(
            String userId,
            String conversationId,
            String keyword,
            String senderId,
            Instant joinedAt,
            MessageSearchSection section,
            String messageId) {
        MessageSearchRequest request = new MessageSearchRequest(
                keyword, conversationId, senderId, null, null, null, null, null);
        Query baseQuery = Objects.requireNonNull(
                buildQuery(userId, request, joinedAt, section, PageRequest.of(0, 1)).getQuery());
        Query query = Query.of(q -> q.bool(b -> b
                .must(baseQuery)
                .filter(f -> f.term(t -> t.field("id").value(messageId)))));

        NativeQuery navigationQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(PageRequest.of(0, 1))
                .withHighlightQuery(buildHighlightQuery())
                .build();

        SearchHits<MessageIndex> hits = esOperations.search(
                navigationQuery,
                MessageIndex.class,
                IndexCoordinates.of(esProperties.getMessageAlias()));

        return hits.hasSearchHits() ? hits.getSearchHit(0) : null;
    }

    private SearchHit<MessageIndex> findNavigationTarget(
            String userId,
            String conversationId,
            String keyword,
            String senderId,
            Instant joinedAt,
            MessageSearchSection section,
            Long from,
            Long to,
            SortOrder sortOrder) {
        MessageSearchRequest request = new MessageSearchRequest(
                keyword, conversationId, senderId, from, to, null, null, null);
        NativeQuery query = buildQuery(userId, request, joinedAt, section, PageRequest.of(0, 1));
        NativeQuery navigationQuery = NativeQuery.builder()
                .withQuery(query.getQuery())
                .withPageable(PageRequest.of(0, 1))
                .withSort(s -> s.field(f -> f.field("createdAt").order(sortOrder)))
                .withHighlightQuery(buildHighlightQuery())
                .build();

        SearchHits<MessageIndex> hits = esOperations.search(
                navigationQuery,
                MessageIndex.class,
                IndexCoordinates.of(esProperties.getMessageAlias()));

        return hits.hasSearchHits() ? hits.getSearchHit(0) : null;
    }

    private long countNavigationMatches(Query query) {
        NativeQuery countQuery = NativeQuery.builder()
                .withQuery(query)
                .build();
        return esOperations.count(countQuery, MessageIndex.class, IndexCoordinates.of(esProperties.getMessageAlias()));
    }

    private int countDisplayIndex(
            String userId,
            String conversationId,
            String keyword,
            String senderId,
            Instant joinedAt,
            MessageSearchSection section,
            Instant targetCreatedAt) {
        if (targetCreatedAt == null) {
            return 1;
        }

        MessageSearchRequest request = new MessageSearchRequest(
                keyword,
                conversationId,
                senderId,
                null,
                targetCreatedAt.toEpochMilli() - 1,
                null,
                null,
                null);
        Query indexQuery = Objects.requireNonNull(
                buildQuery(userId, request, joinedAt, section, Pageable.unpaged()).getQuery());
        long index = countNavigationMatches(indexQuery) + 1;
        return (int) Math.max(1, Math.min(index, Integer.MAX_VALUE));
    }


}
