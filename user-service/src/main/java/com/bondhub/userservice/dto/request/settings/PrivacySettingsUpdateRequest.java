package com.bondhub.userservice.dto.request.settings;

import com.bondhub.userservice.model.enums.PrivacyLevel;
import com.bondhub.userservice.model.enums.DobVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Request to update privacy settings")
public record PrivacySettingsUpdateRequest(
        @Schema(description = "Date of birth visibility", example = "FULL_DATE")
        @NotNull(message = "showDob cannot be null")
        DobVisibility showDob,

        @Schema(description = "Show active status", example = "true")
        @NotNull(message = "showActiveStatus cannot be null")
        Boolean showActiveStatus,

        @Schema(description = "Show read status", example = "true")
        @NotNull(message = "showReadStatus cannot be null")
        Boolean showReadStatus,

        @Schema(description = "Who can text me", example = "EVERYBODY")
        @NotNull(message = "canText cannot be null")
        PrivacyLevel canText,

        @Schema(description = "Who can call me", example = "EVERYBODY")
        @NotNull(message = "canCall cannot be null")
        PrivacyLevel canCall,

        @Schema(description = "Show posts to others", example = "true")
        @NotNull(message = "showPosts cannot be null")
        Boolean showPosts,

        @Schema(description = "Show posts only after this date", example = "2024-01-01T00:00:00")
        LocalDateTime showPostAfter,

        @Schema(description = "Allow search on phone number", example = "true")
        @NotNull(message = "allowSearchOnPhoneNumber cannot be null")
        Boolean allowSearchOnPhoneNumber
) {
}
