package com.bondhub.userservice.dto.request.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Request to update notification settings")
public record NotificationSettingsUpdateRequest(
        @Schema(description = "Global allow notifications toggle", example = "true")
        @NotNull(message = "allowNotifications cannot be null")
        Boolean allowNotifications,

        @Schema(description = "Notification sound toggle", example = "true")
        @NotNull(message = "notifSound cannot be null")
        Boolean notifSound,

        @Schema(description = "Notification vibration toggle", example = "true")
        @NotNull(message = "notifVibration cannot be null")
        Boolean notifVibration,

        @Schema(description = "Friend requests notification toggle", example = "true")
        @NotNull(message = "notifFriendRequests cannot be null")
        Boolean notifFriendRequests,

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
        Boolean previewNewMessage,

        @Schema(description = "Do Not Disturb settings")
        @NotNull(message = "doNotDisturb cannot be null")
        DoNotDisturbUpdateRequest doNotDisturb
) {
    /**
     * Canonical constructor to enforce blocking of notification features
     * when allowNotifications is false.
     */
    public NotificationSettingsUpdateRequest {
        if (Boolean.FALSE.equals(allowNotifications)) {
            notifSound = false;
            notifVibration = false;
            notifFriendRequests = false;
            notifyNewMessageFromDirect = false;
            previewNewMessageFromDirect = false;
            notifyNewMessageFromGroup = false;
            notifyCall = false;
            notifyNewPostFromFriend = false;
            notifyDOB = false;
            notifyNewMessage = false;
            shakeOnNewMessage = false;
            previewNewMessage = false;
            if (doNotDisturb != null) {
                doNotDisturb = new DoNotDisturbUpdateRequest(
                        false,
                        doNotDisturb.dndStartTime(),
                        doNotDisturb.dndEndTime(),
                        doNotDisturb.dndTimezone(),
                        doNotDisturb.activeDays()
                );
            }
        }
    }
    public record DoNotDisturbUpdateRequest(
            @Schema(description = "DND enabled toggle", example = "false")
            @NotNull(message = "dndEnabled cannot be null")
            Boolean dndEnabled,

            @Schema(description = "DND start time", example = "22:00")
            String dndStartTime,

            @Schema(description = "DND end time", example = "07:00")
            String dndEndTime,

            @Schema(description = "DND timezone", example = "GMT+7")
            String dndTimezone,

            @Schema(description = "Active days for DND", example = "[\"MONDAY\", \"TUESDAY\"]")
            List<String> activeDays
    ) {}
}
