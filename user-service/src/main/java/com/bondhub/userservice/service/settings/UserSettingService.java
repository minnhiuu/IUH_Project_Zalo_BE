package com.bondhub.userservice.service.settings;

import com.bondhub.userservice.dto.request.settings.*;
import com.bondhub.userservice.dto.response.settings.UserSettingResponse;
import com.bondhub.userservice.model.UserSetting;

/**
 * Service interface for managing user settings using efficient dot notation updates
 */
public interface UserSettingService {
    
    /**
     * Get all user settings for the current authenticated user
     */
    UserSettingResponse getMySettings();

    /**
     * Get all user settings for a specific user by userId
     */
    UserSettingResponse getSettingsByUserId(String userId);


    /**
     * Update language and interface settings for current user
     */
    UserSettingResponse updateLanguageAndInterfaceSettings(UserSetting.LanguageAndInterface request);

    /**
     * Update notification settings section for current user
     */
    UserSettingResponse updateNotificationSettingsSection(UserSetting.NotificationSettings request);

    /**
     * Update message settings section for current user
     */
    UserSettingResponse updateMessageSettingsSection(UserSetting.MessageSettings request);

    /**
     * Update call settings section for current user
     */
    UserSettingResponse updateCallSettingsSection(UserSetting.CallSettings request);

    /**
     * Update privacy settings section for current user
     */
    UserSettingResponse updatePrivacySettingsSection(UserSetting.PrivacySettings request);

    /**
     * Update contact settings section for current user
     */
    UserSettingResponse updateContactSettingsSection(UserSetting.ContactSettings request);

    /**
     * Update backup and restore settings section for current user
     */
    UserSettingResponse updateBackupRestoreSettingsSection(UserSetting.BackupRestoreSettings request);

    /**
     * Update account security settings section for current user
     */
    UserSettingResponse updateAccountSecuritySettingsSection(UserSetting.AccountSecuritySettings request);

    /**
     * Update journal settings section for current user
     */
    UserSettingResponse updateJournalSettingsSection(UserSetting.JournalSettings request);

    /**
     * Update data on device settings section for current user
     */
    UserSettingResponse updateDataOnDeviceSettingsSection(UserSetting.DataOnDeviceSettings request);

    /**
     * Get language and interface settings
     */
    UserSetting.LanguageAndInterface getLanguageAndInterfaceSettings();

    /**
     * Get specific privacy settings
     */
    UserSetting.PrivacySettings getPrivacySettings();

    /**
     * Get specific notification settings
     */
    UserSetting.NotificationSettings getNotificationSettings();

    /**
     * Get specific message settings
     */
    UserSetting.MessageSettings getMessageSettings();

    /**
     * Get specific call settings
     */
    UserSetting.CallSettings getCallSettings();

    /**
     * Get specific contact settings
     */
    UserSetting.ContactSettings getContactSettings();

    /**
     * Get specific backup and restore settings
     */
    UserSetting.BackupRestoreSettings getBackupRestoreSettings();

    /**
     * Get specific account security settings
     */
    UserSetting.AccountSecuritySettings getAccountSecuritySettings();

    /**
     * Get specific journal settings
     */
    UserSetting.JournalSettings getJournalSettings();

    /**
     * Get specific data on device settings
     */
    UserSetting.DataOnDeviceSettings getDataOnDeviceSettings();

    /**
     * Reset all settings to default for current user
     */
    UserSettingResponse resetToDefaults();
}
