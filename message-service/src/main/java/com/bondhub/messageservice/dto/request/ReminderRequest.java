package com.bondhub.messageservice.dto.request;

import com.bondhub.messageservice.model.enums.ReminderTarget;
import com.bondhub.messageservice.model.enums.RepeatType;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReminderRequest {
    @NotBlank
    String title;
    @NotBlank
    String conversationId;
    String messageId;
    @NotNull
    Instant remindAt;
    @NotNull
    ReminderTarget remindFor;
    @NotNull
    RepeatType repeatType;
}