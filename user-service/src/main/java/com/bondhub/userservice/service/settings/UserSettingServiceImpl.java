package com.bondhub.userservice.service.settings;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.dto.request.settings.*;
import com.bondhub.userservice.dto.response.settings.UserSettingResponse;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.UserSetting;
import com.bondhub.userservice.repository.UserRepository;
import com.bondhub.userservice.repository.UserSettingRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for managing user settings with MongoDB dot notation
 * to avoid loading entire User documents into memory
 */
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class UserSettingServiceImpl implements UserSettingService {

    UserSettingRepository userSettingRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;

    @Override
    public UserSettingResponse getMySettings() {
        String userId = getCurrentUserId();
        log.info("Fetching settings for current user: {}", userId);
        UserSetting userSetting = userSettingRepository.getUserSettingByUserId(userId);
        return UserSettingResponse.fromUserSetting(userSetting);
    }

    @Override
    public UserSettingResponse getSettingsByUserId(String userId) {
        log.info("Fetching settings for userId: {}", userId);
        UserSetting userSetting = userSettingRepository.getUserSettingByUserId(userId);
        return UserSettingResponse.fromUserSetting(userSetting);
    }

    @Override
    @Transactional
    public UserSettingResponse updateGeneralSettings(GeneralSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating general settings for userId: {}", userId);
        
        UserSetting.GeneralSettings settings = new UserSetting.GeneralSettings();
        settings.setShowAllFriends(request.showAllFriends());
        settings.setLanguageEn(request.languageEn());
        
        boolean updated = userSettingRepository.updateGeneralSettings(userId, settings);
        if (!updated) {
            log.warn("No changes were made to general settings for userId: {}", userId);
        }
        
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateSecuritySettings(SecuritySettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating security settings for userId: {}", userId);
        
        UserSetting.SecuritySettings settings = new UserSetting.SecuritySettings();
        settings.setTwoFactorEnabled(request.twoFactorEnabled());
        
        userSettingRepository.updateSecuritySettings(userId, settings);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updatePrivacySettings(PrivacySettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating privacy settings for userId: {}", userId);
        
        UserSetting.PrivacySettings settings = new UserSetting.PrivacySettings();
        settings.setShowDob(request.showDob());
        settings.setShowActiveStatus(request.showActiveStatus());
        settings.setShowReadStatus(request.showReadStatus());
        settings.setCanText(request.canText());
        settings.setCanCall(request.canCall());
        settings.setShowPosts(request.showPosts());
        settings.setShowPostAfter(request.showPostAfter());
        settings.setAllowSearchOnPhoneNumber(request.allowSearchOnPhoneNumber());
        
        userSettingRepository.updatePrivacySettings(userId, settings);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateSyncSettings(SyncSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating sync settings for userId: {}", userId);
        
        UserSetting.SyncSettings settings = new UserSetting.SyncSettings();
        settings.setSyncSuggestion(request.syncSuggestion());
        settings.setShowSyncProgress(request.showSyncProgress());
        
        userSettingRepository.updateSyncSettings(userId, settings);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateAppearanceSettings(AppearanceSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating appearance settings for userId: {}", userId);
        
        UserSetting.AppearanceSettings settings = new UserSetting.AppearanceSettings();
        settings.setTheme(request.theme());
        
        userSettingRepository.updateAppearanceSettings(userId, settings);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateMessageSettings(MessageSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating message settings for userId: {}", userId);
        
        UserSetting.MessageSettings settings = new UserSetting.MessageSettings();
        settings.setQuickResponseEnable(request.quickResponseEnable());
        settings.setSeparatePriorityAndOtherEnable(request.separatePriorityAndOtherEnable());
        settings.setShowTypingStatus(request.showTypingStatus());
        
        userSettingRepository.updateMessageSettings(userId, settings);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateNotificationSettings(NotificationSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating notification settings for userId: {}", userId);
        
        UserSetting.NotificationSettings settings = new UserSetting.NotificationSettings();
        settings.setNotifyNewMessageFromDirect(request.notifyNewMessageFromDirect());
        settings.setPreviewNewMessageFromDirect(request.previewNewMessageFromDirect());
        settings.setNotifyNewMessageFromGroup(request.notifyNewMessageFromGroup());
        settings.setNotifyCall(request.notifyCall());
        settings.setNotifyNewPostFromFriend(request.notifyNewPostFromFriend());
        settings.setNotifyDOB(request.notifyDOB());
        settings.setNotifyNewMessage(request.notifyNewMessage());
        settings.setShakeOnNewMessage(request.shakeOnNewMessage());
        settings.setPreviewNewMessage(request.previewNewMessage());
        
        userSettingRepository.updateNotificationSettings(userId, settings);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateUtilitiesSettings(UtilitiesSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating utilities settings for userId: {}", userId);
        
        UserSetting.UtilitiesSettings settings = new UserSetting.UtilitiesSettings();
        settings.setStickerSuggestion(request.stickerSuggestion());
        
        userSettingRepository.updateUtilitiesSettings(userId, settings);
        return getMySettings();
    }

    @Override
    public UserSetting.GeneralSettings getGeneralSettings() {
        String userId = getCurrentUserId();
        log.info("Fetching general settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "generalSettings", UserSetting.GeneralSettings.class);
    }

    @Override
    public UserSetting.PrivacySettings getPrivacySettings() {
        String userId = getCurrentUserId();
        log.info("Fetching privacy settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "privacySettings", UserSetting.PrivacySettings.class);
    }

    @Override
    public UserSetting.NotificationSettings getNotificationSettings() {
        String userId = getCurrentUserId();
        log.info("Fetching notification settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "notificationSettings", UserSetting.NotificationSettings.class);
    }

    @Override
    @Transactional
    public UserSettingResponse resetToDefaults() {
        String userId = getCurrentUserId();
        log.info("Resetting all settings to defaults for userId: {}", userId);
        
        UserSetting defaultSettings = new UserSetting();
        userSettingRepository.updateUserSetting(userId, defaultSettings);
        
        return UserSettingResponse.fromUserSetting(defaultSettings);
    }

    /**
     * Helper method to get current user ID from account ID
     */
    private String getCurrentUserId() {
        String accountId = securityUtil.getCurrentAccountId();
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String userId = user.getUserId();

        return userId;
    }
}
