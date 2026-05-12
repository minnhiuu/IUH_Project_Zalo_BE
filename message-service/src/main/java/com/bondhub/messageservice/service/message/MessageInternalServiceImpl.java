package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.client.messageservice.RecentChatInteractionRequest;
import com.bondhub.common.dto.client.messageservice.RecentChatInteractionResponse;
import com.bondhub.common.enums.MessageStatus;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.messageservice.dto.response.MessageSyncResponse;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.service.conversation.ConversationHelper;
import com.bondhub.messageservice.util.SearchableTextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageInternalServiceImpl implements MessageInternalService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final ConversationHelper conversationHelper;
    private final SecurityUtil securityUtil;
    private final MongoTemplate mongoTemplate;

    private static final int RECENT_INTERACTION_DAYS = 30;
    private static final int SMALL_GROUP_MEMBER_LIMIT = 10;
    private static final double SENT_BY_ME_WEIGHT = 0.15;
    private static final double SENT_BY_ME_SCORE_CAP = 2.0;
    private static final double SENT_BY_TARGET_WEIGHT = 0.25;
    private static final double SENT_BY_TARGET_SCORE_CAP = 2.5;
    private static final double TWO_WAY_CONVERSATION_BOOST = 0.5;
    private static final double GROUP_MESSAGE_WEIGHT = 0.05;
    private static final double GROUP_INTERACTION_SCORE_CAP = 1.0;
    private static final double CHAT_SCORE_CAP = 5.0;

    @Override
    public List<MessageSyncResponse> getMessagesBatch(String lastId, int batchSize) {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<Message> messages;

        if (lastId == null || lastId.isEmpty()) {
            messages = messageRepository.findAllByOrderByIdAsc(pageable);
        } else {
            messages = messageRepository.findByIdGreaterThanOrderByIdAsc(lastId, pageable);
        }

        return messages.stream().map(this::mapToSyncResponse).collect(Collectors.toList());
    }

    @Override
    public long getMessageCount() {
        return messageRepository.count();
    }

    @Override
    public List<RecentChatInteractionResponse> getRecentChatInteractions(RecentChatInteractionRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null || request == null || request.targetUserIds() == null || request.targetUserIds().isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime since = LocalDateTime.now().minusDays(RECENT_INTERACTION_DAYS);
        List<String> targetUserIds = normalizeTargetUserIds(request.targetUserIds(), currentUserId);
        if (targetUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Conversation> directConversationByTarget = findDirectConversationByTarget(currentUserId, targetUserIds);
        Map<String, String> targetByDirectConversationId = directConversationByTarget.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getValue().getId(), Map.Entry::getKey));
        List<String> directConversationIds = new ArrayList<>(targetByDirectConversationId.keySet());
        Map<String, DirectMessageStats> directStatsByConversationId = fetchDirectMessageStats(
                directConversationIds,
                currentUserId,
                targetByDirectConversationId,
                since);
        Map<String, Double> groupScoreByTarget = fetchSmallGroupScores(currentUserId, targetUserIds, since);

        return targetUserIds.stream()
                .map(targetUserId -> buildRecentChatInteraction(
                        targetUserId,
                        directConversationByTarget.get(targetUserId),
                        directStatsByConversationId,
                        groupScoreByTarget.getOrDefault(targetUserId, 0.0)))
                .filter(response -> response.chatInteractionScore() > 0.0)
                .toList();
    }

    private List<String> normalizeTargetUserIds(List<String> targetUserIds, String currentUserId) {
        return targetUserIds.stream()
                .filter(Objects::nonNull)
                .filter(targetUserId -> !targetUserId.isBlank())
                .filter(targetUserId -> !targetUserId.equals(currentUserId))
                .distinct()
                .toList();
    }

    private Map<String, Conversation> findDirectConversationByTarget(String currentUserId, List<String> targetUserIds) {
        return conversationRepository.findDirectConversationsByUserAndTargets(currentUserId, targetUserIds)
                .stream()
                .map(conversation -> new TargetConversation(resolveOtherDirectMemberId(conversation, currentUserId), conversation))
                .filter(item -> item.targetUserId() != null)
                .collect(Collectors.toMap(
                        TargetConversation::targetUserId,
                        TargetConversation::conversation,
                        (existing, replacement) -> existing));
    }

    private String resolveOtherDirectMemberId(Conversation conversation, String currentUserId) {
        if (conversation == null || conversation.getMembers() == null) {
            return null;
        }

        return conversation.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .map(ConversationMember::getUserId)
                .filter(Objects::nonNull)
                .filter(userId -> !userId.equals(currentUserId))
                .findFirst()
                .orElse(null);
    }

    private Map<String, DirectMessageStats> fetchDirectMessageStats(
            List<String> conversationIds,
            String currentUserId,
            Map<String, String> targetByConversationId,
            LocalDateTime since) {
        if (conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> senderIds = new HashSet<>(targetByConversationId.values());
        senderIds.add(currentUserId);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("conversationId").in(conversationIds)
                        .and("createdAt").gte(since)
                        .and("status").ne(MessageStatus.REVOKED)
                        .and("senderId").in(senderIds)),
                Aggregation.group("conversationId", "senderId")
                        .count().as("count")
                        .max("createdAt").as("lastMessageAt")
        );

        List<Document> results = mongoTemplate.aggregate(
                aggregation,
                "chat_messages",
                Document.class).getMappedResults();
        Map<String, DirectMessageStats> statsByConversationId = new HashMap<>();

        for (Document row : results) {
            MessageCountAggregationId id = readAggregationId(row);
            if (id == null) {
                continue;
            }

            String targetUserId = targetByConversationId.get(id.conversationId());
            DirectMessageStats previous = statsByConversationId.getOrDefault(
                    id.conversationId(),
                    DirectMessageStats.empty());

            DirectMessageStats next = id.senderId().equals(currentUserId)
                    ? previous.withSentByMe(readCount(row), readInstant(row.get("lastMessageAt")))
                    : id.senderId().equals(targetUserId)
                            ? previous.withSentByTarget(readCount(row), readInstant(row.get("lastMessageAt")))
                            : previous;
            statsByConversationId.put(id.conversationId(), next);
        }

        return statsByConversationId;
    }

    private Map<String, Double> fetchSmallGroupScores(String currentUserId, List<String> targetUserIds, LocalDateTime since) {
        List<Conversation> smallSharedGroups = conversationRepository.findSharedGroupConversationsByUserAndTargets(
                        currentUserId,
                        targetUserIds)
                .stream()
                .filter(this::isSmallActiveGroup)
                .toList();
        if (smallSharedGroups.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> targetIdsByConversationId = smallSharedGroups.stream()
                .collect(Collectors.toMap(
                        Conversation::getId,
                        conversation -> activeTargetMembers(conversation, currentUserId, targetUserIds),
                        (existing, replacement) -> existing));
        List<String> groupConversationIds = targetIdsByConversationId.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        if (groupConversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> relevantSenders = new HashSet<>(targetUserIds);
        relevantSenders.add(currentUserId);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("conversationId").in(groupConversationIds)
                        .and("createdAt").gte(since)
                        .and("status").ne(MessageStatus.REVOKED)
                        .and("senderId").in(relevantSenders)),
                Aggregation.group("conversationId", "senderId")
                        .count().as("count")
        );

        List<Document> results = mongoTemplate.aggregate(
                aggregation,
                "chat_messages",
                Document.class).getMappedResults();
        Map<String, Map<String, Long>> countsByConversationAndSender = new HashMap<>();
        for (Document row : results) {
            MessageCountAggregationId id = readAggregationId(row);
            if (id == null) {
                continue;
            }

            countsByConversationAndSender
                    .computeIfAbsent(id.conversationId(), ignored -> new HashMap<>())
                    .put(id.senderId(), readCount(row));
        }

        Map<String, Double> groupScoreByTarget = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : targetIdsByConversationId.entrySet()) {
            Map<String, Long> senderCounts = countsByConversationAndSender.getOrDefault(entry.getKey(), Collections.emptyMap());
            long currentUserCount = senderCounts.getOrDefault(currentUserId, 0L);

            for (String targetUserId : entry.getValue()) {
                long targetCount = senderCounts.getOrDefault(targetUserId, 0L);
                if (currentUserCount <= 0 || targetCount <= 0) {
                    continue;
                }

                double score = calculateGroupInteractionScore(currentUserCount + targetCount);
                groupScoreByTarget.merge(targetUserId, score, Math::max);
            }
        }

        return groupScoreByTarget;
    }

    private boolean isSmallActiveGroup(Conversation conversation) {
        if (conversation == null || !conversation.isGroup() || conversation.getMembers() == null) {
            return false;
        }

        long activeMemberCount = conversation.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .count();
        return activeMemberCount <= SMALL_GROUP_MEMBER_LIMIT;
    }

    private List<String> activeTargetMembers(Conversation conversation, String currentUserId, List<String> targetUserIds) {
        Set<String> targetSet = Set.copyOf(targetUserIds);
        return conversation.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .map(ConversationMember::getUserId)
                .filter(Objects::nonNull)
                .filter(userId -> !userId.equals(currentUserId))
                .filter(targetSet::contains)
                .toList();
    }

    private RecentChatInteractionResponse buildRecentChatInteraction(
            String targetUserId,
            Conversation directConversation,
            Map<String, DirectMessageStats> directStatsByConversationId,
            double groupInteractionScore) {
        DirectMessageStats directStats = directConversation != null
                ? directStatsByConversationId.getOrDefault(directConversation.getId(), DirectMessageStats.empty())
                : DirectMessageStats.empty();
        Instant lastMessageAt = directConversation != null ? resolveLastMessageAt(directConversation, directStats) : null;
        DirectScoreBreakdown directScoreBreakdown = calculateDirectChatScore(directStats, lastMessageAt);
        double chatInteractionScore = calculateFinalChatInteractionScore(
                directScoreBreakdown.directChatScore(),
                groupInteractionScore);

        return RecentChatInteractionResponse.builder()
                .userId(targetUserId)
                .lastMessageAt(lastMessageAt)
                .messageCount30d(directStats.totalCount())
                .sentByMeCount30d(directStats.sentByMeCount())
                .sentByTargetCount30d(directStats.sentByTargetCount())
                .twoWayConversation(directStats.twoWayConversation())
                .volumeScore(directScoreBreakdown.volumeScore())
                .recencyBoost(directScoreBreakdown.recencyBoost())
                .directChatScore(directScoreBreakdown.directChatScore())
                .groupInteractionScore(groupInteractionScore)
                .chatInteractionScore(chatInteractionScore)
                .build();
    }

    private Instant resolveLastMessageAt(Conversation conversation, DirectMessageStats stats) {
        if (stats != null && stats.lastMessageAt() != null) {
            return stats.lastMessageAt();
        }

        if (conversation.getLastMessage() != null && conversation.getLastMessage().getTimestamp() != null) {
            return conversation.getLastMessage().getTimestamp().toInstant(ZoneOffset.UTC);
        }

        return messageRepository.findByConversationIdAndStatusNotOrderByCreatedAtDesc(
                        conversation.getId(),
                        MessageStatus.REVOKED,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(Message::getCreatedAt)
                .map(createdAt -> createdAt.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    private DirectScoreBreakdown calculateDirectChatScore(DirectMessageStats stats, Instant lastMessageAt) {
        if (stats.totalCount() <= 0 || lastMessageAt == null) {
            return new DirectScoreBreakdown(0.0, 0.0, 0.0);
        }

        double volumeScore = Math.min(stats.sentByMeCount() * SENT_BY_ME_WEIGHT, SENT_BY_ME_SCORE_CAP)
                + Math.min(stats.sentByTargetCount() * SENT_BY_TARGET_WEIGHT, SENT_BY_TARGET_SCORE_CAP)
                + (stats.twoWayConversation() ? TWO_WAY_CONVERSATION_BOOST : 0.0);
        double recencyBoost = calculateRecencyBoost(lastMessageAt);
        double directChatScore = Math.min(volumeScore + recencyBoost, CHAT_SCORE_CAP);
        return new DirectScoreBreakdown(volumeScore, recencyBoost, directChatScore);
    }

    private double calculateGroupInteractionScore(long commonGroupMessageCount30d) {
        return Math.min(Math.max(commonGroupMessageCount30d, 0L) * GROUP_MESSAGE_WEIGHT, GROUP_INTERACTION_SCORE_CAP);
    }

    private double calculateFinalChatInteractionScore(double directChatScore, double groupInteractionScore) {
        if (directChatScore > 0.0) {
            return directChatScore;
        }
        return Math.min(groupInteractionScore, CHAT_SCORE_CAP);
    }

    private double calculateRecencyBoost(Instant lastInteractionAt) {
        long days = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(lastInteractionAt, Instant.now()));
        return Math.exp(-days / 7.0);
    }

    private MessageCountAggregationId readAggregationId(Document row) {
        Object rawId = row.get("_id");
        if (!(rawId instanceof Document id)) {
            return null;
        }

        String conversationId = id.getString("conversationId");
        String senderId = id.getString("senderId");
        if (conversationId == null || senderId == null) {
            return null;
        }

        return new MessageCountAggregationId(conversationId, senderId);
    }

    private long readCount(Document row) {
        Object rawCount = row.get("count");
        return rawCount instanceof Number number ? number.longValue() : 0L;
    }

    private Instant readInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }

    private MessageSyncResponse mapToSyncResponse(Message message) {
        boolean hasAttachment = message.getAttachments() != null && !message.getAttachments().isEmpty();
        boolean hasLink = message.getLinkPreview() != null;
        ConversationIndexMetadata conversationMetadata = resolveConversationMetadata(message.getConversationId());
        
        String originalFileName = null;
        Long size = null;
        String linkGroupName = null;

        if (hasAttachment) {
            var first = message.getAttachments().getFirst();
            originalFileName = first.getOriginalFileName() != null ? first.getOriginalFileName() : first.getFileName();
            size = first.getSize();
        }

        String linkUrl = hasLink ? message.getLinkPreview().getUrl() : null;

        return MessageSyncResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .participantIds(conversationMetadata.participantIds())
                .participantNames(conversationMetadata.participantNames())
                .participantAvatars(conversationMetadata.participantAvatars())
                .conversationName(conversationMetadata.conversationName())
                .conversationAvatar(conversationMetadata.conversationAvatar())
                .group(conversationMetadata.group())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(message.getSenderAvatar())
                .content(message.getContent())
                .type(message.getType())
                .status(message.getStatus())
                .hasAttachment(hasAttachment)
                .hasLink(hasLink)
                .linkGroupName(linkGroupName)
                .linkUrl(linkUrl)
                .originalFileName(originalFileName)
                .size(size)
                .searchableText(SearchableTextBuilder.build(message))
                .conversationSearchText(conversationMetadata.conversationSearchText())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toInstant(ZoneOffset.UTC) : null)
                .deletedBy(message.getDeletedBy())
                .visibleTo(message.getVisibleTo())
                .build();
    }

    private ConversationIndexMetadata resolveConversationMetadata(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return ConversationIndexMetadata.empty();
        }

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) {
            return ConversationIndexMetadata.empty();
        }

        List<ConversationMember> activeMembers = conversation.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .toList();
        List<String> participantIds = activeMembers.stream()
                .map(ConversationMember::getUserId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, user -> user));
        List<String> participantNames = participantIds.stream()
                .map(userCache::get)
                .filter(Objects::nonNull)
                .map(ChatUser::getFullName)
                .filter(Objects::nonNull)
                .toList();
        List<String> participantAvatars = participantIds.stream()
                .map(userCache::get)
                .filter(Objects::nonNull)
                .map(ChatUser::getAvatar)
                .toList();
        String conversationName = conversation.isGroup()
                ? resolveConversationName(conversation, userCache)
                : null;
        String conversationSearchText = buildConversationSearchText(conversationName, participantNames);

        return new ConversationIndexMetadata(
                participantIds,
                participantNames,
                participantAvatars,
                conversationName,
                conversation.getAvatar(),
                conversation.isGroup(),
                conversationSearchText);
    }

    private String resolveConversationName(Conversation conversation, Map<String, ChatUser> userCache) {
        if (conversation.getName() != null && !conversation.getName().isBlank()) {
            return conversation.getName().trim();
        }

        return conversationHelper.getDynamicGroupName(conversation, null, userCache);
    }

    private String buildConversationSearchText(String conversationName, List<String> participantNames) {
        List<String> parts = new ArrayList<>();
        if (conversationName != null && !conversationName.isBlank()) {
            parts.add(conversationName.trim());
        }
        if (participantNames != null && !participantNames.isEmpty()) {
            parts.addAll(participantNames);
        }

        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private record ConversationIndexMetadata(
            List<String> participantIds,
            List<String> participantNames,
            List<String> participantAvatars,
            String conversationName,
            String conversationAvatar,
            boolean group,
            String conversationSearchText
    ) {
        private static ConversationIndexMetadata empty() {
            return new ConversationIndexMetadata(List.of(), List.of(), List.of(), null, null, false, null);
        }
    }

    private record TargetConversation(String targetUserId, Conversation conversation) {
    }

    private record MessageCountAggregationId(
            String conversationId,
            String senderId
    ) {
    }

    private record DirectMessageStats(
            int sentByMeCount,
            int sentByTargetCount,
            Instant lastMessageAt
    ) {
        private static DirectMessageStats empty() {
            return new DirectMessageStats(0, 0, null);
        }

        private int totalCount() {
            return sentByMeCount + sentByTargetCount;
        }

        private boolean twoWayConversation() {
            return sentByMeCount > 0 && sentByTargetCount > 0;
        }

        private DirectMessageStats withSentByMe(long count, Instant lastMessageAt) {
            return new DirectMessageStats(
                    Math.toIntExact(Math.min(count, Integer.MAX_VALUE)),
                    sentByTargetCount,
                    latest(this.lastMessageAt, lastMessageAt));
        }

        private DirectMessageStats withSentByTarget(long count, Instant lastMessageAt) {
            return new DirectMessageStats(
                    sentByMeCount,
                    Math.toIntExact(Math.min(count, Integer.MAX_VALUE)),
                    latest(this.lastMessageAt, lastMessageAt));
        }

        private Instant latest(Instant first, Instant second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            return first.isAfter(second) ? first : second;
        }

    }

    private record DirectScoreBreakdown(
            double volumeScore,
            double recencyBoost,
            double directChatScore
    ) {
    }
}
