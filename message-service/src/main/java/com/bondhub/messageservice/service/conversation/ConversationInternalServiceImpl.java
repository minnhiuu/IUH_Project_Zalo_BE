package com.bondhub.messageservice.service.conversation;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.messageservice.ConversationMemberLookupResponse;
import com.bondhub.common.dto.client.messageservice.ConversationSearchResponse;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationInternalServiceImpl implements ConversationInternalService {

    private final ConversationRepository conversationRepository;
    private final ConversationHelper helper;
    private final ChatUserRepository chatUserRepository;
    private final MongoTemplate mongoTemplate;
    private final S3UtilV2 s3UtilV2;

    @Override
    public ConversationMemberLookupResponse getConversationMember(String conversationId, String userId) {
        ConversationMember member = conversationRepository.findById(conversationId)
                .flatMap(conversation -> conversation.getMembers().stream()
                        .filter(helper::isActiveMember)
                        .filter(item -> item.getUserId().equals(userId))
                        .findFirst())
                .orElse(null);

        if (member == null) {
            return ConversationMemberLookupResponse.builder()
                    .member(false)
                    .joinedAt(null)
                    .build();
        }

        return ConversationMemberLookupResponse.builder()
                .member(true)
                .joinedAt(member.getJoinedAt() != null
                        ? member.getJoinedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : null)
                .build();
    }

    @Override
    public PageResponse<List<ConversationSearchResponse>> searchConversations(
            String userId,
            String keyword,
            Boolean isGroup,
            int page,
            int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String normalizedKeyword = normalize(keyword);

        Query query = new Query(Criteria.where("members")
                .elemMatch(Criteria.where("userId").is(userId).and("active").ne(false)))
                .with(Sort.by(Sort.Direction.DESC, "lastMessage.timestamp"));

        List<Conversation> conversations = mongoTemplate.find(query, Conversation.class);
        if (conversations.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        Set<String> userIds = new HashSet<>();
        conversations.forEach(conversation -> conversation.getMembers().stream()
                .filter(helper::isActiveMember)
                .map(ConversationMember::getUserId)
                .forEach(userIds::add));

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, user -> user));
        String baseUrl = s3UtilV2.getS3BaseUrl();

        List<ConversationSearchResponse> filtered = conversations.stream()
                .map(conversation -> toSearchResponse(conversation, userId, userCache, baseUrl, keyword))
                .filter(Objects::nonNull)
                .filter(response -> isGroup == null || response.group() == isGroup)
                .filter(response -> matches(response, normalizedKeyword))
                .sorted(Comparator.comparing(ConversationSearchResponse::group)
                        .thenComparing(ConversationSearchResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int total = filtered.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        List<ConversationSearchResponse> pageData = start < total
                ? filtered.subList(start, end)
                : Collections.emptyList();

        return PageResponse.fromPage(
                new PageImpl<>(pageData, pageable, total),
                item -> item);
    }

    private ConversationSearchResponse toSearchResponse(
            Conversation conversation,
            String viewerId,
            Map<String, ChatUser> userCache,
            String baseUrl,
            String keyword) {
        List<ConversationMember> activeMembers = conversation.getMembers().stream()
                .filter(helper::isActiveMember)
                .toList();
        int memberCount = activeMembers.size();

        if (conversation.isGroup()) {
            String name = conversation.getName();
            if (name == null || name.isBlank()) {
                name = helper.getDynamicGroupName(conversation, viewerId, userCache);
            }

            List<String> participantNames = activeMembers.stream()
                    .map(m -> userCache.get(m.getUserId()))
                    .filter(Objects::nonNull)
                    .map(u -> helper.safeDisplayName(u.getFullName()))
                    .toList();

            List<String> participantAvatars = activeMembers.stream()
                    .map(m -> userCache.get(m.getUserId()))
                    .filter(Objects::nonNull)
                    .map(u -> u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                    .toList();

            return ConversationSearchResponse.builder()
                    .conversationId(conversation.getId())
                    .name(name)
                    .avatar(conversation.getAvatar() != null ? baseUrl + conversation.getAvatar() : null)
                    .group(true)
                    .memberCount(memberCount)
                    .participantNames(participantNames)
                    .participantAvatars(participantAvatars)
                    .displayHighlights(highlight(name, keyword))
                    .build();
        }

        String recipientId = activeMembers.stream()
                .map(ConversationMember::getUserId)
                .filter(memberId -> !memberId.equals(viewerId))
                .findFirst()
                .orElse(viewerId);
        ChatUser partner = helper.resolvePartner(recipientId, viewerId, userCache);
        String name = helper.safeDisplayName(partner.getFullName());

        return ConversationSearchResponse.builder()
                .conversationId(conversation.getId())
                .recipientId(recipientId)
                .name(name)
                .avatar(partner.getAvatar() != null ? baseUrl + partner.getAvatar() : null)
                .group(false)
                .memberCount(memberCount)
                .displayHighlights(highlight(name, keyword))
                .phoneNumber(partner.getPhoneNumber())
                .build();
    }

    private String highlight(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return text;
        }

        String normalizedText = normalize(text);
        String normalizedKeyword = normalize(keyword);

        int index = normalizedText.indexOf(normalizedKeyword);
        if (index < 0) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        while (index >= 0) {
            sb.append(text, cursor, index);
            
            sb.append("<em>")
              .append(text, index, index + keyword.length())
              .append("</em>");
            
            cursor = index + keyword.length();
            index = normalizedText.indexOf(normalizedKeyword, cursor);
        }
        sb.append(text.substring(cursor));
        
        return sb.toString();
    }

    private boolean matches(ConversationSearchResponse response, String normalizedKeyword) {
        if (normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return true;
        }

        return normalize(response.name()).contains(normalizedKeyword);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim();
    }
}
