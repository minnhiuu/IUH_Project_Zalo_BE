package com.bondhub.messageservice.service.reminder;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.messageservice.dto.request.ReminderRequest;
import com.bondhub.messageservice.dto.response.ReminderResponse;
import com.bondhub.messageservice.mapper.ReminderMapper;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Message;
import com.bondhub.messageservice.model.Reminder;
import com.bondhub.messageservice.model.enums.ReminderStatus;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ReminderRepository;
import com.bondhub.messageservice.service.message.SystemMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final ReminderRepository reminderRepository;
    private final ReminderMapper reminderMapper;
    private final SystemMessageService systemMessageService;
    private final ChatUserRepository chatUserRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public ReminderResponse createReminder(ReminderRequest request, String creatorId) {
        Reminder reminder = reminderMapper.toEntity(request);
        reminder.setCreatorId(creatorId);
        reminder.setNextRemindAt(request.getRemindAt());
        Reminder saved = reminderRepository.save(reminder);

        ChatUser creator = chatUserRepository.findById(creatorId).orElse(null);
        String actorName = creator != null && creator.getFullName() != null
            ? creator.getFullName()
            : "Nguoi dung";
        String actorAvatar = creator != null ? creator.getAvatar() : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", saved.getTitle());
        payload.put("remindAt", saved.getRemindAt());
        payload.put("reminderId", saved.getId());
        if (saved.getMessageId() != null) {
            payload.put("messageId", saved.getMessageId());
        }

        Map<String, Object> extraMetadata = new HashMap<>();
        extraMetadata.put("payload", payload);

        systemMessageService.sendSystemMessage(
            saved.getConversationId(),
            creatorId,
            actorName,
            actorAvatar,
            SystemActionType.REMINDER,
            extraMetadata
        );

        return reminderMapper.toResponse(saved);
    }

    @Override
    public ReminderResponse updateReminder(String reminderId, ReminderRequest request, String userId) {
        Reminder reminder = reminderRepository.findById(reminderId)
            .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!reminder.getCreatorId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        reminder.setTitle(request.getTitle());
        reminder.setRemindAt(request.getRemindAt());
        reminder.setRemindFor(request.getRemindFor());
        reminder.setRepeatType(request.getRepeatType());
        if (request.getMessageId() != null) {
            reminder.setMessageId(request.getMessageId());
        }
        reminder.setNextRemindAt(request.getRemindAt());
        reminder.setStatus(ReminderStatus.ACTIVE);

        Reminder saved = reminderRepository.save(reminder);

        ChatUser creator = chatUserRepository.findById(userId).orElse(null);
        String actorName = creator != null && creator.getFullName() != null
            ? creator.getFullName()
            : "Nguoi dung";
        String actorAvatar = creator != null ? creator.getAvatar() : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", saved.getTitle());
        payload.put("remindAt", saved.getRemindAt());
        payload.put("reminderId", saved.getId());
        payload.put("editAction", true);
        if (saved.getMessageId() != null) {
            payload.put("messageId", saved.getMessageId());
        }

        Map<String, Object> extraMetadata = new HashMap<>();
        extraMetadata.put("payload", payload);

        systemMessageService.sendSystemMessage(
            saved.getConversationId(),
            userId,
            actorName,
            actorAvatar,
            SystemActionType.REMINDER,
            extraMetadata
        );

        return reminderMapper.toResponse(saved);
    }

    @Override
    public List<ReminderResponse> getReminders(String conversationId) {
        return reminderRepository.findByConversationId(conversationId).stream()
                .map(reminderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteReminder(String id, String userId) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!reminder.getCreatorId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        ChatUser creator = chatUserRepository.findById(userId).orElse(null);
        String actorName = creator != null && creator.getFullName() != null
            ? creator.getFullName()
            : "Nguoi dung";
        String actorAvatar = creator != null ? creator.getAvatar() : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", reminder.getTitle());
        payload.put("remindAt", reminder.getRemindAt());
        payload.put("reminderId", reminder.getId());
        payload.put("deleteAction", true);
        payload.put("deleteNotice", true);
        if (reminder.getMessageId() != null) {
            payload.put("messageId", reminder.getMessageId());
        }

        Map<String, Object> extraMetadata = new HashMap<>();
        extraMetadata.put("payload", payload);

        systemMessageService.sendSystemMessage(
            reminder.getConversationId(),
            userId,
            actorName,
            actorAvatar,
            SystemActionType.REMINDER,
            extraMetadata
        );

        markReminderMessagesDeleted(reminder);

        reminderRepository.delete(reminder);
    }

    private void markReminderMessagesDeleted(Reminder reminder) {
        if (reminder == null || reminder.getId() == null) {
            return;
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("conversationId").is(reminder.getConversationId()));
        query.addCriteria(Criteria.where("type").is(com.bondhub.common.enums.MessageType.SYSTEM));
        query.addCriteria(Criteria.where("metadata.action").is(SystemActionType.REMINDER.name()));
        query.addCriteria(Criteria.where("metadata.payload.reminderId").is(reminder.getId()));

        Update update = new Update();
        update.set("metadata.payload.deleteAction", true);
        update.set("metadata.payload.hasTriggered", true);

        mongoTemplate.updateMulti(query, update, Message.class);
    }
}