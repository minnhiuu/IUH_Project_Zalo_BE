package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update security settings")
public record SecuritySettingsUpdateRequest(
        @Schema(description = "Enable or disable two-factor authentication", example = "false")
        @NotNull(message = "twoFactorEnabled cannot be null")
        Boolean twoFactorEnabled
) {
}
