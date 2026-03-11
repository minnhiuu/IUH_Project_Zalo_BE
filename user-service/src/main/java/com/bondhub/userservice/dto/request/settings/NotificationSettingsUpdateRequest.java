package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update notification settings")
public record NotificationSettingsUpdateRequest(
        @Schema(description = "Notify on new direct message", example = "true")
        @NotNull(message = "notifyNewMessageFromDirect cannot be null")
        Boolean notifyNewMessageFromDirect,

        @Schema(description = "Preview new direct messages", example = "true")
        @NotNull(message = "previewNewMessageFromDirect cannot be null")
        Boolean previewNewMessageFromDirect,

        @Schema(description = "Notify on new group message", example = "true")
        @NotNull(message = "notifyNewMessageFromGroup cannot be null")
        Boolean notifyNewMessageFromGroup,

        @Schema(description = "Notify on incoming call", example = "true")
        @NotNull(message = "notifyCall cannot be null")
        Boolean notifyCall,

        @Schema(description = "Notify on new post from friend", example = "true")
        @NotNull(message = "notifyNewPostFromFriend cannot be null")
        Boolean notifyNewPostFromFriend,

        @Schema(description = "Notify on friend's birthday", example = "true")
        @NotNull(message = "notifyDOB cannot be null")
        Boolean notifyDOB,

        @Schema(description = "Notify on new message in app", example = "true")
        @NotNull(message = "notifyNewMessage cannot be null")
        Boolean notifyNewMessage,

        @Schema(description = "Shake on new message", example = "true")
        @NotNull(message = "shakeOnNewMessage cannot be null")
        Boolean shakeOnNewMessage,

        @Schema(description = "Preview new messages", example = "true")
        @NotNull(message = "previewNewMessage cannot be null")
        Boolean previewNewMessage
) {
}
