package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update message settings")
public record MessageSettingsUpdateRequest(
        @Schema(description = "Enable quick response", example = "false")
        @NotNull(message = "quickResponseEnable cannot be null")
        Boolean quickResponseEnable,

        @Schema(description = "Enable separation of priority and other messages", example = "true")
        @NotNull(message = "separatePriorityAndOtherEnable cannot be null")
        Boolean separatePriorityAndOtherEnable,

        @Schema(description = "Show typing status", example = "true")
        @NotNull(message = "showTypingStatus cannot be null")
        Boolean showTypingStatus
) {
}
