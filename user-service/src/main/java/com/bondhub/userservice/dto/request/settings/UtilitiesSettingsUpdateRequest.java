package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update utilities settings")
public record UtilitiesSettingsUpdateRequest(
        @Schema(description = "Enable sticker suggestion", example = "true")
        @NotNull(message = "stickerSuggestion cannot be null")
        Boolean stickerSuggestion
) {
}
