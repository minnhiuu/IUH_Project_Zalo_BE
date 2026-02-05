package com.bondhub.userservice.dto.response.settings;

import com.bondhub.userservice.model.UserSetting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing user settings")
public record UserSettingResponse(
        @Schema(description = "General settings")
        UserSetting.GeneralSettings generalSettings,

        @Schema(description = "Security settings")
        UserSetting.SecuritySettings securitySettings,

        @Schema(description = "Privacy settings")
        UserSetting.PrivacySettings privacySettings,

        @Schema(description = "Sync settings")
        UserSetting.SyncSettings syncSettings,

        @Schema(description = "Appearance settings")
        UserSetting.AppearanceSettings appearanceSettings,

        @Schema(description = "Message settings")
        UserSetting.MessageSettings messageSettings,

        @Schema(description = "Notification settings")
        UserSetting.NotificationSettings notificationSettings,

        @Schema(description = "Utilities settings")
        UserSetting.UtilitiesSettings utilitiesSettings
) {
    public static UserSettingResponse fromUserSetting(UserSetting userSetting) {
        return new UserSettingResponse(
                userSetting.getGeneralSettings(),
                userSetting.getSecuritySettings(),
                userSetting.getPrivacySettings(),
                userSetting.getSyncSettings(),
                userSetting.getAppearanceSettings(),
                userSetting.getMessageSettings(),
                userSetting.getNotificationSettings(),
                userSetting.getUtilitiesSettings()
        );
    }
}
