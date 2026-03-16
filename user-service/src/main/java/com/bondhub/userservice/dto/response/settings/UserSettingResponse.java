package com.bondhub.userservice.dto.response.settings;

import com.bondhub.userservice.model.UserSetting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing user settings")
public record UserSettingResponse(
        @Schema(description = "General settings")
        UserSetting.LanguageAndInterface languageAndInterface,

    @Schema(description = "Notification settings")
    UserSetting.NotificationSettings notificationSettings,

    @Schema(description = "Message settings")
    UserSetting.MessageSettings messageSettings,

    @Schema(description = "Call settings")
    UserSetting.CallSettings callSettings,

        @Schema(description = "Privacy settings")
        UserSetting.PrivacySettings privacySettings,

    @Schema(description = "Contact settings")
    UserSetting.ContactSettings contactSettings,

    @Schema(description = "Backup and restore settings")
    UserSetting.BackupRestoreSettings backupRestoreSettings,

    @Schema(description = "Account security settings")
    UserSetting.AccountSecuritySettings accountSecuritySettings,

    @Schema(description = "Journal settings")
    UserSetting.JournalSettings journalSettings,

    @Schema(description = "Data on device settings")
    UserSetting.DataOnDeviceSettings dataOnDeviceSettings
) {
    public static UserSettingResponse fromUserSetting(UserSetting userSetting) {
        return new UserSettingResponse(
                userSetting.getLanguageAndInterface(),
        userSetting.getNotificationSettings(),
        userSetting.getMessageSettings(),
        userSetting.getCallSettings(),
                userSetting.getPrivacySettings(),
        userSetting.getContactSettings(),
        userSetting.getBackupRestoreSettings(),
        userSetting.getAccountSecuritySettings(),
        userSetting.getJournalSettings(),
        userSetting.getDataOnDeviceSettings()
        );
    }
}
