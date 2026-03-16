package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update appearance settings")
public record AppearanceSettingsUpdateRequest(
        @Schema(description = "Theme preference - true for light, false for dark", example = "true")
        @NotNull(message = "theme cannot be null")
        Boolean theme
) {
}
