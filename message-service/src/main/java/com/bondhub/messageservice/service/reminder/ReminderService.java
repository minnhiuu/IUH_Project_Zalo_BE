package com.bondhub.messageservice.service.reminder;

import com.bondhub.messageservice.dto.request.ReminderRequest;
import com.bondhub.messageservice.dto.response.ReminderResponse;
import java.util.List;

public interface ReminderService {
    ReminderResponse createReminder(ReminderRequest request, String creatorId);
    ReminderResponse updateReminder(String reminderId, ReminderRequest request, String userId);
    List<ReminderResponse> getReminders(String conversationId);
    void deleteReminder(String id, String userId);
}