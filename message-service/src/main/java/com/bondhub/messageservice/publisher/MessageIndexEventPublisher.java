package com.bondhub.messageservice.publisher;

import com.bondhub.common.event.message.MessageIndexRequestedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.model.AttachmentInfo;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.LinkPreview;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.service.conversation.ConversationHelper;
import com.bondhub.messageservice.util.SearchableTextBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageIndexEventPublisher {

    private static final Pattern JOIN_LINK_PATTERN = Pattern.compile("^https?://[^/]+/g/([a-zA-Z0-9_-]+)$");

    OutboxEventPublisher outboxEventPublisher;
    ConversationRepository conversationRepository;
    ChatUserRepository chatUserRepository;
    ConversationHelper conversationHelper;

    @Transactional
    public void publishIndexRequest(Message message) {
        if (message == null || message.getType() == MessageType.SYSTEM) {
            return;
        }

        String searchableText = SearchableTextBuilder.build(message);
        Instant createdAt = message.getCreatedAt() != null
                ? message.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null;
        AttachmentInfo primaryAttachment = resolvePrimaryAttachment(message);
        LinkPreview linkPreview = message.getLinkPreview();
        String resolvedLinkGroupName = resolveLinkGroupName(linkPreview);
        String resolvedLinkUrl = resolveLinkUrl(message, linkPreview);
        ConversationIndexMetadata conversationMetadata = resolveConversationMetadata(message.getConversationId());

        MessageIndexRequestedEvent event = MessageIndexRequestedEvent.builder()
                .messageId(message.getId())
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
                .linkGroupName(resolvedLinkGroupName)
                .linkUrl(resolvedLinkUrl)
                .originalFileName(resolveOriginalFileName(primaryAttachment))
                .size(primaryAttachment != null ? primaryAttachment.getSize() : null)
                .searchableText(searchableText)
                .conversationSearchText(conversationMetadata.conversationSearchText())
                .type(message.getType() != null ? message.getType().name() : null)
                .status(message.getStatus() != null ? message.getStatus().name() : null)
                .hasAttachment(message.getAttachments() != null && !message.getAttachments().isEmpty())
                .hasLink(message.getLinkPreview() != null)
                .createdAt(createdAt)
                .deletedBy(message.getDeletedBy() != null ? new ArrayList<>(message.getDeletedBy()) : List.of())
                .visibleTo(message.getVisibleTo() != null ? new ArrayList<>(message.getVisibleTo()) : List.of())
                .build();

        outboxEventPublisher.saveAndPublish(
                message.getId(),
                "Message",
                EventType.MESSAGE_INDEX_REQUESTED,
                event
        );

        log.info("Published MESSAGE_INDEX_REQUESTED: messageId={}", message.getId());
    }

    private AttachmentInfo resolvePrimaryAttachment(Message message) {
        if (message.getAttachments() == null || message.getAttachments().isEmpty()) {
            return null;
        }
        return message.getAttachments().get(0);
    }

    private String resolveOriginalFileName(AttachmentInfo attachmentInfo) {
        if (attachmentInfo == null) {
            return null;
        }
        return attachmentInfo.getOriginalFileName() != null
                ? attachmentInfo.getOriginalFileName()
                : attachmentInfo.getFileName();
    }

    private ConversationIndexMetadata resolveConversationMetadata(String conversationId) {
        if (!hasText(conversationId)) {
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
        if (hasText(conversation.getName())) {
            return conversation.getName().trim();
        }

        return conversationHelper.getDynamicGroupName(conversation, null, userCache);
    }

    private String buildConversationSearchText(String conversationName, List<String> participantNames) {
        List<String> parts = new ArrayList<>();
        if (hasText(conversationName)) {
            parts.add(conversationName.trim());
        }
        if (participantNames != null && !participantNames.isEmpty()) {
            parts.addAll(participantNames);
        }

        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private String resolveLinkGroupName(LinkPreview linkPreview) {
        if (linkPreview == null) {
            return null;
        }

        if (hasText(linkPreview.getGroupName())) {
            return linkPreview.getGroupName().trim();
        }

        String token = resolveJoinLinkToken(linkPreview);
        if (!hasText(token)) {
            return null;
        }

        Conversation conversation = conversationRepository.findByJoinLinkToken(token).orElse(null);
        if (conversation == null || !conversation.isGroup()) {
            return null;
        }

        String conversationName = conversation.getName();
        if (hasText(conversationName)) {
            return conversationName.trim();
        }

        Set<String> memberIds = conversation.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, user -> user));

        return conversationHelper.getDynamicGroupName(conversation, null, userCache);
    }

    private String resolveLinkUrl(Message message, LinkPreview linkPreview) {
        if (linkPreview != null && hasText(linkPreview.getUrl())) {
            return linkPreview.getUrl().trim();
        }

        if (message != null && hasText(message.getContent())) {
            Matcher matcher = JOIN_LINK_PATTERN.matcher(message.getContent().trim());
            if (matcher.matches()) {
                return message.getContent().trim();
            }
        }

        return null;
    }

    private String resolveJoinLinkToken(LinkPreview linkPreview) {
        if (hasText(linkPreview.getToken())) {
            return linkPreview.getToken().trim();
        }

        if (!hasText(linkPreview.getUrl())) {
            return null;
        }

        Matcher matcher = JOIN_LINK_PATTERN.matcher(linkPreview.getUrl().trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
}
