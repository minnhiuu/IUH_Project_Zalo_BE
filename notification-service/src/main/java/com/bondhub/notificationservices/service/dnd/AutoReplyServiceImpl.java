package com.bondhub.notificationservices.service.dnd;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.chat.SendAutoReplyEvent;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.publisher.SendAutoReplyPublisher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AutoReplyServiceImpl implements AutoReplyService {

    StringRedisTemplate redisTemplate;
    SendAutoReplyPublisher sendAutoReplyPublisher;

    static Duration AUTO_REPLY_TTL = Duration.ofHours(24);
    static String MESSAGE_KEY = "chat.quiet_mode.auto_reply";

    @Override
    public void replyIfNeeded(BatchedNotificationEvent event) {
        if (event.getType() != NotificationType.MESSAGE_DIRECT) {
            return;
        }

        String quietUserId = event.getRecipientId();
        String senderId = event.getLastActorId();
        String conversationId = extractConversationId(event);

        if (quietUserId == null || senderId == null || conversationId == null) {
            log.warn("[AutoReply] Missing data: quietUserId={}, senderId={}, conversationId={}",
                    quietUserId, senderId, conversationId);
            return;
        }

        String redisKey = buildRedisKey(quietUserId, senderId);

        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                "1",
                AUTO_REPLY_TTL
        );

        if (!Boolean.TRUE.equals(firstTime)) {
            log.debug("[AutoReply] Skip duplicate auto-reply: quietUser={}, sender={}",
                    quietUserId, senderId);
            return;
        }

        SendAutoReplyEvent autoReplyEvent = SendAutoReplyEvent.builder()
                .conversationId(conversationId)
                .quietUserId(quietUserId)
                .receiverId(senderId)
                .messageKey(MESSAGE_KEY)
                .build();

        sendAutoReplyPublisher.publish(autoReplyEvent);

        log.info("[AutoReply] Published auto-reply: quietUser={}, sender={}, conversation={}",
                quietUserId, senderId, conversationId);
    }

    private String buildRedisKey(String quietUserId, String senderId) {
        return "quiet:auto-reply:" + quietUserId + ":" + senderId;
    }

    private String extractConversationId(BatchedNotificationEvent event) {
        // Try to get from payload first as referenceId is often the messageId
        if (event.getRawPayloads() != null && !event.getRawPayloads().isEmpty()) {
            Map<String, Object> payload = event.getRawPayloads().get(event.getRawPayloads().size() - 1);
            Object conversationId = payload.get("conversationId");
            if (conversationId != null) {
                return conversationId.toString();
            }
        }

        // Fallback to referenceId
        return event.getReferenceId();
    }
}
