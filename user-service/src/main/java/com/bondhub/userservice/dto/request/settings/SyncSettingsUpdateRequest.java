package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update sync settings")
public record SyncSettingsUpdateRequest(
        @Schema(description = "Enable sync suggestion", example = "true")
        @NotNull(message = "syncSuggestion cannot be null")
        Boolean syncSuggestion,

        @Schema(description = "Show sync progress", example = "true")
        @NotNull(message = "showSyncProgress cannot be null")
        Boolean showSyncProgress
) {
}
