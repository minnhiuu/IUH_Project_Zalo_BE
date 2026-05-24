package com.bondhub.messageservice.service.reminder;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.enums.SystemActionType;
import com.bondhub.messageservice.dto.request.ReminderRequest;
import com.bondhub.messageservice.dto.response.ReminderResponse;
import com.bondhub.messageservice.mapper.ReminderMapper;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.model.Reminder;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.messageservice.repository.ReminderRepository;
import com.bondhub.messageservice.service.message.SystemMessageService;
import lombok.RequiredArgsConstructor;
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
        reminderRepository.delete(reminder);
    }
}