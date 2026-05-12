package com.bondhub.messageservice.service.message;

import com.bondhub.common.dto.client.messageservice.ChatInteractionFeatureSnapshotResponse;
import com.bondhub.common.enums.MessageStatus;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageInternalServiceImpl implements MessageInternalService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final ConversationHelper conversationHelper;
    private final MongoTemplate mongoTemplate;

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
    public List<ChatInteractionFeatureSnapshotResponse> getSearchInteractionFeatureSnapshot(int sinceDays, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(sinceDays, 1));
        int boundedLimit = Math.min(Math.max(limit, 1), 10_000);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(since)
                        .and("status").ne(MessageStatus.REVOKED)),
                Aggregation.group("conversationId")
                        .count().as("messageCount")
                        .max("createdAt").as("lastMessageAt"),
                Aggregation.sort(Sort.Direction.DESC, "lastMessageAt"),
                Aggregation.limit(boundedLimit)
        );

        List<Document> rows = mongoTemplate.aggregate(
                aggregation,
                "chat_messages",
                Document.class).getMappedResults();
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, ConversationSnapshot> snapshotByConversationId = new HashMap<>();
        for (Document row : rows) {
            String conversationId = row.getString("_id");
            if (conversationId == null) {
                continue;
            }

            snapshotByConversationId.put(
                    conversationId,
                    new ConversationSnapshot(readCount(row), readInstant(row.get("lastMessageAt"))));
        }

        return conversationRepository.findAllById(snapshotByConversationId.keySet())
                .stream()
                .filter(conversation -> !conversation.isGroup())
                .map(conversation -> toDirectMemberPair(conversation, snapshotByConversationId.get(conversation.getId())))
                .filter(Objects::nonNull)
                .flatMap(pair -> pair.toResponses().stream())
                .sorted(Comparator.comparing(ChatInteractionFeatureSnapshotResponse::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(boundedLimit)
                .toList();
    }

    private DirectConversationSnapshot toDirectMemberPair(Conversation conversation, ConversationSnapshot snapshot) {
        if (conversation == null || conversation.getMembers() == null || snapshot == null) {
            return null;
        }

        List<String> memberIds = conversation.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .map(ConversationMember::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (memberIds.size() != 2) {
            return null;
        }

        return new DirectConversationSnapshot(memberIds.get(0), memberIds.get(1), snapshot);
    }

    private long readCount(Document row) {
        Object value = row.get("messageCount");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Instant readInstant(Object value) {
        if (value instanceof java.util.Date date) {
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

    private record ConversationSnapshot(long messageCount, Instant lastMessageAt) {
    }

    private record DirectConversationSnapshot(
            String firstUserId,
            String secondUserId,
            ConversationSnapshot snapshot
    ) {
        private List<ChatInteractionFeatureSnapshotResponse> toResponses() {
            int count = Math.toIntExact(Math.min(snapshot.messageCount(), Integer.MAX_VALUE));
            return List.of(
                    ChatInteractionFeatureSnapshotResponse.builder()
                            .userId(firstUserId)
                            .targetUserId(secondUserId)
                            .messageCount30d(count)
                            .lastMessageAt(snapshot.lastMessageAt())
                            .build(),
                    ChatInteractionFeatureSnapshotResponse.builder()
                            .userId(secondUserId)
                            .targetUserId(firstUserId)
                            .messageCount30d(count)
                            .lastMessageAt(snapshot.lastMessageAt())
                            .build()
            );
        }
    }
}
