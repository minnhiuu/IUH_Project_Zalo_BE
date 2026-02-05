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
     * Update general settings for current user
     */
    UserSettingResponse updateGeneralSettings(GeneralSettingsUpdateRequest request);

    /**
     * Update security settings for current user
     */
    UserSettingResponse updateSecuritySettings(SecuritySettingsUpdateRequest request);

    /**
     * Update privacy settings for current user
     */
    UserSettingResponse updatePrivacySettings(PrivacySettingsUpdateRequest request);

    /**
     * Update sync settings for current user
     */
    UserSettingResponse updateSyncSettings(SyncSettingsUpdateRequest request);

    /**
     * Update appearance settings for current user
     */
    UserSettingResponse updateAppearanceSettings(AppearanceSettingsUpdateRequest request);

    /**
     * Update message settings for current user
     */
    UserSettingResponse updateMessageSettings(MessageSettingsUpdateRequest request);

    /**
     * Update notification settings for current user
     */
    UserSettingResponse updateNotificationSettings(NotificationSettingsUpdateRequest request);

    /**
     * Update utilities settings for current user
     */
    UserSettingResponse updateUtilitiesSettings(UtilitiesSettingsUpdateRequest request);

    /**
     * Get specific general settings
     */
    UserSetting.GeneralSettings getGeneralSettings();

    /**
     * Get specific privacy settings
     */
    UserSetting.PrivacySettings getPrivacySettings();

    /**
     * Get specific notification settings
     */
    UserSetting.NotificationSettings getNotificationSettings();

    /**
     * Reset all settings to default for current user
     */
    UserSettingResponse resetToDefaults();
}
