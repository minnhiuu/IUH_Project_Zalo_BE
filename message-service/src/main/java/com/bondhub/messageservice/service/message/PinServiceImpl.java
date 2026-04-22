package com.bondhub.messageservice.service.message;

import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.messageservice.model.*;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinServiceImpl implements PinService {

    private static final int MAX_PINS = 3;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatUserRepository chatUserRepository;
    private final SystemMessageService systemMessageService;
    private final MongoTemplate mongoTemplate;
    private final SecurityUtil securityUtil;
    private final S3UtilV2 s3UtilV2;

    @Override
    public List<PinnedMessageInfo> getPins(String conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        List<PinnedMessageInfo> pins = conv.getPinnedMessages();
        return pins != null ? pins : Collections.emptyList();
    }

    @Override
    public PinnedMessageInfo pinMessage(String conversationId, String messageId) {
        String actorId = securityUtil.getCurrentUserId();
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        List<PinnedMessageInfo> pins = conv.getPinnedMessages() != null
                ? new ArrayList<>(conv.getPinnedMessages())
                : new ArrayList<>();

        if (pins.size() >= MAX_PINS) {
            throw new AppException(ErrorCode.CHAT_MAX_PINNED_MESSAGES);
        }
        if (pins.stream().anyMatch(p -> p.getMessageId().equals(messageId))) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_ALREADY_PINNED);
        }

        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        ChatUser actor = chatUserRepository.findById(actorId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Người dùng";

        String snapshot = buildSnapshot(msg);

        PinnedMessageInfo pin = PinnedMessageInfo.builder()
                .messageId(messageId)
                .pinnedBy(actorId)
                .pinnedByName(actorName)
                .contentSnapshot(snapshot)
                .messageType(msg.getType())
                .pinnedAt(LocalDateTime.now())
                .build();

        pins.add(0, pin); // newest first

        Query query = new Query(Criteria.where("id").is(conversationId));
        Update update = new Update().set("pinnedMessages", pins);
        mongoTemplate.updateFirst(query, update, Conversation.class);

        // System message
        Map<String, Object> meta = new HashMap<>();
        meta.put("messageId", messageId);
        meta.put("contentSnapshot", snapshot);
        meta.put("originalSenderName", actorName);
        
        meta.put("originalSenderAvatar", s3UtilV2.getFullUrl(actor != null ? actor.getAvatar() : null));

        meta.put("originalContent", msg.getContent());
        
        systemMessageService.sendSystemMessage(conversationId, actorId, actorName,
                actor != null ? actor.getAvatar() : null,
                SystemActionType.PIN_MESSAGE, meta);

        return pin;
    }

    @Override
    public void unpinMessage(String conversationId, String messageId) {
        String actorId = securityUtil.getCurrentUserId();
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        List<PinnedMessageInfo> pins = conv.getPinnedMessages() != null
                ? new ArrayList<>(conv.getPinnedMessages())
                : new ArrayList<>();

        boolean removed = pins.removeIf(p -> p.getMessageId().equals(messageId));
        if (!removed) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_NOT_PINNED);
        }

        Query query = new Query(Criteria.where("id").is(conversationId));
        Update update = new Update().set("pinnedMessages", pins);
        mongoTemplate.updateFirst(query, update, Conversation.class);

        ChatUser actor = chatUserRepository.findById(actorId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Người dùng";

        // Fetch original message to provide complete metadata
        Message msg = messageRepository.findById(messageId).orElse(null);

        Map<String, Object> meta = new HashMap<>();
        meta.put("messageId", messageId);
        
        if (msg != null) {
            meta.put("originalSenderId", msg.getSenderId());
            meta.put("originalSenderName", actorName);
            
            meta.put("originalSenderAvatar", s3UtilV2.getFullUrl(actor != null ? actor.getAvatar() : null));

            meta.put("originalContent", msg.getContent());
        }

        systemMessageService.sendSystemMessage(conversationId, actorId, actorName,
                actor != null ? actor.getAvatar() : null,
                SystemActionType.UNPIN_MESSAGE, meta);
    }

    private String buildSnapshot(Message msg) {
        if (msg.getContent() != null && !msg.getContent().isBlank()) {
            return msg.getContent().length() > 100
                    ? msg.getContent().substring(0, 100) + "..."
                    : msg.getContent();
        }
        if (msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
            return "[Tệp đính kèm]";
        }
        return "[Tin nhắn]";
    }
}
