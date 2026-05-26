package com.bondhub.messageservice.dto.response;

import com.bondhub.messageservice.model.enums.ReminderStatus;
import com.bondhub.messageservice.model.enums.ReminderTarget;
import com.bondhub.messageservice.model.enums.RepeatType;
import lombok.Data;
import java.time.Instant;

@Data
public class ReminderResponse {
    String id;
    String title;
    String conversationId;
    String messageId;
    String creatorId;
    Instant remindAt;
    Instant nextRemindAt;
    Instant lastTriggeredAt;
    ReminderTarget remindFor;
    RepeatType repeatType;
    ReminderStatus status;
    Instant createdAt;
    Instant updatedAt;
}