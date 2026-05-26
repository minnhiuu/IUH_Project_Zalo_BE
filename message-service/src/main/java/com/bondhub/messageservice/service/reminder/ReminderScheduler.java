package com.bondhub.messageservice.service.reminder;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.enums.MessageType;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.common.publisher.RawNotificationEventPublisher;
import com.bondhub.common.utils.S3UtilV2;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Conversation;
import com.bondhub.messageservice.model.ConversationMember;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.Reminder;
import com.bondhub.messageservice.model.enums.ReminderStatus;
import com.bondhub.messageservice.model.enums.RepeatType;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ConversationRepository;
import com.bondhub.messageservice.repository.MessageRepository;
import com.bondhub.messageservice.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderRepository reminderRepository;
    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final RawNotificationEventPublisher rawNotificationEventPublisher;
    private final S3UtilV2 s3UtilV2;

    @Scheduled(fixedDelayString = "${reminder.scheduler.delay-ms:10000}")
    public void processReminders() {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(5);
        Instant windowEnd = now.plusSeconds(30);

        List<Reminder> dueWithNext = reminderRepository.findByNextRemindAtBetweenAndStatus(
                windowStart, windowEnd, ReminderStatus.ACTIVE);
        List<Reminder> dueWithLegacy = reminderRepository.findByRemindAtBetweenAndStatus(
                windowStart, windowEnd, ReminderStatus.ACTIVE);

        Map<String, Reminder> dueMap = new LinkedHashMap<>();
        for (Reminder reminder : dueWithNext) {
            dueMap.put(reminder.getId(), reminder);
        }
        for (Reminder reminder : dueWithLegacy) {
            dueMap.putIfAbsent(reminder.getId(), reminder);
        }

        if (dueMap.isEmpty()) {
            return;
        }

        String baseUrl = s3UtilV2.getS3BaseUrl();

        for (Reminder reminder : dueMap.values()) {
            Instant triggerAt = reminder.getNextRemindAt() != null
                    ? reminder.getNextRemindAt()
                    : reminder.getRemindAt();

            if (triggerAt == null || triggerAt.isBefore(windowStart) || triggerAt.isAfter(windowEnd)) {
                continue;
            }

            Conversation conversation = conversationRepository.findById(reminder.getConversationId()).orElse(null);
            if (conversation == null) {
                log.warn("Reminder {} skipped: conversation not found", reminder.getId());
                continue;
            }

            ChatUser creator = reminder.getCreatorId() != null
                    ? chatUserRepository.findById(reminder.getCreatorId()).orElse(null)
                    : null;

            String actorName = creator != null && creator.getFullName() != null
                    ? creator.getFullName()
                    : "Nguoi dung";
            String actorAvatar = creator != null && creator.getAvatar() != null
                    ? normalizeAvatar(baseUrl, creator.getAvatar())
                    : "";

                persistReminderTriggerMessage(reminder, conversation, triggerAt, actorName, actorAvatar);
                markReminderCreateMessageTriggered(reminder, conversation.getId(), triggerAt);

            Map<String, Object> payload = new HashMap<>();
            payload.put("conversationId", conversation.getId());
            payload.put("reminderId", reminder.getId());
            payload.put("messageId", reminder.getMessageId());
            payload.put("title", reminder.getTitle());
            payload.put("message", reminder.getTitle() != null ? reminder.getTitle() : "Nhac nho");
            payload.put("groupName", conversation.getName() != null ? conversation.getName() : "");
            payload.put("triggeredAt", triggerAt.toString());
            payload.put("repeatType", reminder.getRepeatType() != null ? reminder.getRepeatType().name() : null);

            for (ConversationMember member : conversation.getMembers()) {
                if (member.getActive() == null || !member.getActive()) {
                    continue;
                }

                String recipientId = member.getUserId();
                if (recipientId == null || recipientId.isBlank()) {
                    continue;
                }

                RawNotificationEvent event = RawNotificationEvent.builder()
                        .recipientId(recipientId)
                        .actorId(reminder.getCreatorId())
                        .actorName(actorName)
                        .actorAvatar(actorAvatar)
                        .type(NotificationType.REMINDER)
                        .referenceId(reminder.getId())
                        .payload(payload)
                        .occurredAt(LocalDateTime.now())
                        .build();

                rawNotificationEventPublisher.publish(event);
            }

            reminder.setLastTriggeredAt(Instant.now());
            updateNextReminder(reminder, triggerAt);
            reminderRepository.save(reminder);
        }
    }

    private void persistReminderTriggerMessage(Reminder reminder, Conversation conversation, Instant triggerAt,
                                               String actorName, String actorAvatar) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("reminderId", reminder.getId());
            payload.put("title", reminder.getTitle());
            payload.put("message", reminder.getTitle() != null ? reminder.getTitle() : "Nhac nho");
            payload.put("remindAt", reminder.getRemindAt() != null ? reminder.getRemindAt().toString() : null);
            payload.put("triggeredAt", triggerAt != null ? triggerAt.toString() : null);
            payload.put("messageId", reminder.getMessageId());
            payload.put("groupName", conversation.getName() != null ? conversation.getName() : "");
            payload.put("repeatType", reminder.getRepeatType() != null ? reminder.getRepeatType().name() : null);
            payload.put("isTriggerMessage", true);
            payload.put("hasTriggered", true);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("action", SystemActionType.REMINDER.name());
            metadata.put("actorName", actorName);
            metadata.put("payload", payload);

            LocalDateTime createdAt = triggerAt != null
                ? LocalDateTime.ofInstant(triggerAt, ZoneOffset.UTC)
                : LocalDateTime.now();

            Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(reminder.getCreatorId())
                .senderName(actorName)
                .senderAvatar(actorAvatar)
                .type(MessageType.SYSTEM)
                .metadata(metadata)
                .build();
            message.setCreatedAt(createdAt);

        messageRepository.save(message);
    }

    private void markReminderCreateMessageTriggered(Reminder reminder, String conversationId, Instant triggerAt) {
        if (reminder == null || reminder.getId() == null || conversationId == null) {
            return;
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("conversationId").is(conversationId));
        query.addCriteria(Criteria.where("type").is(MessageType.SYSTEM));
        query.addCriteria(Criteria.where("metadata.action").is(SystemActionType.REMINDER.name()));
        query.addCriteria(Criteria.where("metadata.payload.reminderId").is(reminder.getId()));
        query.addCriteria(Criteria.where("metadata.payload.isTriggerMessage").ne(true));

        Update update = new Update();
        update.set("metadata.payload.hasTriggered", true);
        if (triggerAt != null) {
            update.set("metadata.payload.triggeredAt", triggerAt.toString());
        }

        mongoTemplate.updateMulti(query, update, Message.class);
    }

    private void updateNextReminder(Reminder reminder, Instant baseTime) {
        RepeatType repeatType = reminder.getRepeatType();
        if (repeatType == null || repeatType == RepeatType.NONE) {
            reminder.setStatus(ReminderStatus.COMPLETED);
            reminder.setNextRemindAt(null);
            return;
        }

        Instant next = computeNextRemindAt(baseTime, repeatType);
        if (next == null) {
            reminder.setStatus(ReminderStatus.COMPLETED);
            reminder.setNextRemindAt(null);
            return;
        }

        reminder.setNextRemindAt(next);
    }

    private Instant computeNextRemindAt(Instant baseTime, RepeatType repeatType) {
        if (baseTime == null) {
            return null;
        }

        ZonedDateTime base = baseTime.atZone(ZoneOffset.UTC);
        return switch (repeatType) {
            case DAILY -> base.plusDays(1).toInstant();
            case WEEKLY -> base.plusWeeks(1).toInstant();
            case MULTIPLE_DAYS_WEEKLY -> base.plusWeeks(1).toInstant();
            case MONTHLY -> base.plusMonths(1).toInstant();
            case YEARLY -> base.plusYears(1).toInstant();
            default -> null;
        };
    }

    private String normalizeAvatar(String baseUrl, String avatar) {
        if (avatar == null || avatar.isBlank()) {
            return "";
        }
        if (avatar.startsWith("http")) {
            return avatar;
        }
        return baseUrl + avatar;
    }
}
