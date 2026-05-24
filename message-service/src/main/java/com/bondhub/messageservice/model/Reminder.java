package com.bondhub.messageservice.model;

import com.bondhub.common.model.BaseModel;
import com.bondhub.messageservice.model.enums.ReminderStatus;
import com.bondhub.messageservice.model.enums.ReminderTarget;
import com.bondhub.messageservice.model.enums.RepeatType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reminders")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class Reminder extends BaseModel {
    @Id
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
    @Builder.Default
    ReminderStatus status = ReminderStatus.ACTIVE;
}