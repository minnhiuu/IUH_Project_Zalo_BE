package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update general settings")
public record GeneralSettingsUpdateRequest(
        @Schema(description = "Show all friends or only BondHub friends", example = "false")
        @NotNull(message = "showAllFriends cannot be null")
        Boolean showAllFriends,

        @Schema(description = "Language preference - true for English, false for other", example = "false")
        @NotNull(message = "languageEn cannot be null")
        Boolean languageEn
) {
}
