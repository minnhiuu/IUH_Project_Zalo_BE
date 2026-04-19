package com.bondhub.searchservice.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3Util;
import com.bondhub.searchservice.client.ConversationMemberClient;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.request.MessageSearchRequest;
import com.bondhub.searchservice.dto.response.MessageSearchResponse;
import com.bondhub.searchservice.model.elasticsearch.MessageIndex;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageSearchServiceImpl implements MessageSearchService {

    ElasticsearchOperations esOperations;
    ElasticsearchProperties esProperties;
    ConversationMemberClient conversationMemberClient;

    @NonFinal
    @Value("${aws.s3.bucket.name}")
    String bucketName;

    @NonFinal
    @Value("${cloud.aws.region.static}")
    String region;

    @Override
    public PageResponse<List<MessageSearchResponse>> searchMessages(
            String userId,
            MessageSearchRequest request,
            Pageable pageable) {
        ApiResponse<ConversationMemberLookupResponse> membershipResponse =
                conversationMemberClient.getConversationMember(request.conversationId(), userId);

        ConversationMemberLookupResponse membership =
                membershipResponse != null ? membershipResponse.data() : null;

        if (membership == null || !membership.member()) {
            throw new AppException(ErrorCode.CHAT_MEMBER_NOT_FOUND);
        }

        NativeQuery query = buildQuery(userId, request, membership.joinedAt(), pageable);

        SearchHits<MessageIndex> hits = esOperations.search(
                query,
                MessageIndex.class,
                IndexCoordinates.of(esProperties.getMessageAlias())
        );

        SearchPage<MessageIndex> page = SearchHitSupport.searchPageFor(hits, pageable);
        return PageResponse.fromPage(page, hit -> this.toResponse(hit, request.keyword()));
    }

    private NativeQuery buildQuery(
            String userId,
            MessageSearchRequest request,
            Instant joinedAt,
            Pageable pageable) {

        Instant lowerBound = resolveLowerBound(request, joinedAt);
        Instant upperBound = request.to();
        boolean hasKeyword = hasText(request.keyword());

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

            b.filter(f -> f.term(t -> t
                    .field("conversationId")
                    .value(request.conversationId())
            ));

            if (request.senderId() != null) {
                b.filter(f -> f.term(t -> t
                        .field("senderId")
                        .value(request.senderId())
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
            lowerBounds.add(request.from());
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

        return MessageSearchResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(message.getSenderAvatar() != null ? S3Util.getS3BaseUrl(bucketName, region) + message.getSenderAvatar() : null)
                .content(message.getContent())
                .size(message.getSize())
                .type(message.getType())
                .status(message.getStatus())
                .hasAttachment(message.isHasAttachment())
                .hasLink(message.isHasLink())
                .createdAt(message.getCreatedAt())
                .highlights(resolveHighlight(hit, message, keyword))
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

            if (fallback == null) return null;
            fragment = fallback.length() > 150 ? fallback.substring(0, 150) + "..." : fallback;
        }

        // Precise highlight: If the match is a prefix, only highlight the prefix part
        if (hasText(keyword) && fragment.contains("<em>") && fragment.contains("</em>")) {
            try {
                String normalizedKeyword = normalizeForComparison(keyword);
                int startTag = fragment.indexOf("<em>");
                int endTag = fragment.indexOf("</em>");
                String termWithTags = fragment.substring(startTag, endTag + 5);
                String innerTerm = fragment.substring(startTag + 4, endTag);
                String normalizedInner = normalizeForComparison(innerTerm);

                int matchIndex = normalizedInner.indexOf(normalizedKeyword);
                if (matchIndex != -1) {
                    // Re-construct: prefix before tags + <em> + matched part + </em> + rest of word + rest of fragment
                    int matchEnd = matchIndex + normalizedKeyword.length();
                    String actualMatch = innerTerm.substring(matchIndex, matchEnd);
                    String restOfInner = innerTerm.substring(matchEnd);
                    
                    return fragment.substring(0, startTag) 
                            + innerTerm.substring(0, matchIndex)
                            + "<em>" + actualMatch + "</em>" 
                            + restOfInner 
                            + fragment.substring(endTag + 5);
                }
            } catch (Exception e) {
                log.warn("Failed to refine highlight for keyword: {}", keyword, e);
            }
        }

        return fragment;
    }

    private String normalizeForComparison(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
