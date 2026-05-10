package com.bondhub.userservice.service.settings;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.JwtUtil;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.dto.response.settings.UserSettingResponse;
import com.bondhub.common.event.user.UserPrivacyChangedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.UserSetting;
import com.bondhub.userservice.model.enums.AppLanguage;
import com.bondhub.userservice.repository.UserRepository;
import com.bondhub.userservice.repository.UserSettingRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

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
    JwtUtil jwtUtil;
    HttpServletRequest httpServletRequest;
    AuthServiceClient authServiceClient;
    OutboxEventPublisher outboxEventPublisher;

    @Override
    public UserSettingResponse getMySettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching settings for current user: {}", userId);
        UserSetting userSetting = userSettingRepository.getUserSettingByUserId(userId);
        applyCurrentDeviceLanguage(userSetting.getLanguageAndInterface());
        applyCurrentDeviceNotificationPreference(userSetting.getNotificationSettings());
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
    public UserSettingResponse updateLanguageAndInterfaceSettings(UserSetting.LanguageAndInterface request) {
        String userId = getCurrentUserId(true);
        log.info("Updating languageAndInterface section for userId: {}", userId);

        UserSetting.LanguageAndInterface settings = userSettingRepository.getNestedSetting(
                userId,
                "languageAndInterface",
                UserSetting.LanguageAndInterface.class);
        if (settings == null) {
            settings = new UserSetting.LanguageAndInterface();
        }

        if (request.getThemeMode() != null) {
            settings.setThemeMode(request.getThemeMode());
        }
        if (request.getFontScale() > 0) {
            settings.setFontScale(request.getFontScale());
        }

        AppLanguage requestedLanguage = request.getLanguage();
        if (requestedLanguage != null) {
            settings.setLanguage(requestedLanguage);

            if (settings.getLanguageByDeviceId() == null) {
                settings.setLanguageByDeviceId(new HashMap<>());
            }

            String deviceId = getCurrentDeviceId();
            if (deviceId != null && !deviceId.isBlank()) {
                settings.getLanguageByDeviceId().put(deviceId, requestedLanguage);
            }
        }

        userSettingRepository.updateSettingSection(userId, "languageAndInterface", settings);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateNotificationSettingsSection(UserSetting.NotificationSettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating notificationSettings section for userId: {}", userId);

        UserSetting.NotificationSettings current = userSettingRepository.getNestedSetting(
                userId,
                "notificationSettings",
                UserSetting.NotificationSettings.class);

        if (current == null) {
            current = new UserSetting.NotificationSettings();
        }
        if (request == null) {
            request = new UserSetting.NotificationSettings();
        }

        // If allowNotifications is false, disable all other notification features
        if (!request.isAllowNotifications()) {
            request.setNotifSound(false);
            request.setNotifVibration(false);
            request.setNotifMessages(false);
            request.setNotifGroups(false);
            request.setNotifFriendRequests(false);
            if (request.getDoNotDisturb() != null) {
                request.getDoNotDisturb().setDndEnabled(false);
            }
        }

        HashMap<String, UserSetting.DeviceNotificationSettings> notificationSettingsByDeviceId = new HashMap<>();
        if (current.getNotificationSettingsByDeviceId() != null) {
            notificationSettingsByDeviceId.putAll(current.getNotificationSettingsByDeviceId());
        }
        if (request.getNotificationSettingsByDeviceId() != null) {
            notificationSettingsByDeviceId.putAll(request.getNotificationSettingsByDeviceId());
        }

        String deviceId = getCurrentDeviceId();
        if (deviceId != null && !deviceId.isBlank()) {
            notificationSettingsByDeviceId.put(deviceId, toDeviceNotificationSettings(request, deviceId));
        }

        request.setNotificationSettingsByDeviceId(notificationSettingsByDeviceId);

        userSettingRepository.updateSettingSection(userId, "notificationSettings", request);

        // Publish event for synchronization
        publishNotificationSettingsUpdatedEvent(userId, deviceId, request);

        return getMySettings();
    }

    private void publishNotificationSettingsUpdatedEvent(String userId, String deviceId, UserSetting.NotificationSettings request) {
        var event = com.bondhub.common.event.user.NotificationSettingsUpdatedEvent.builder()
                .userId(userId)
                .deviceId(deviceId)
                .globalSettings(com.bondhub.common.event.user.NotificationSettingsUpdatedEvent.GlobalSettings.builder()
                        .allowNotifications(request.isAllowNotifications())
                        .notifSound(request.isNotifSound())
                        .notifVibration(request.isNotifVibration())
                        .notifMessages(request.isNotifMessages())
                        .notifGroups(request.isNotifGroups())
                        .notifFriendRequests(request.isNotifFriendRequests())
                        .dndSettings(request.getDoNotDisturb() == null ? null :
                                com.bondhub.common.event.user.NotificationSettingsUpdatedEvent.DndSettings.builder()
                                        .dndEnabled(request.getDoNotDisturb().isDndEnabled())
                                        .dndStartTime(request.getDoNotDisturb().getDndStartTime())
                                        .dndEndTime(request.getDoNotDisturb().getDndEndTime())
                                        .dndTimezone(request.getDoNotDisturb().getDndTimezone())
                                        .activeDays(request.getDoNotDisturb().getActiveDays() == null ? null : 
                                            request.getDoNotDisturb().getActiveDays().stream().map(Enum::name).toList())
                                        .build())
                        .build())
                .deviceSettingsMap(new java.util.HashMap<>())
                .build();

        if (request.getNotificationSettingsByDeviceId() != null) {
            request.getNotificationSettingsByDeviceId().forEach((dId, settings) -> {
                event.getDeviceSettingsMap().put(dId, com.bondhub.common.event.user.NotificationSettingsUpdatedEvent.DeviceSettings.builder()
                        .allowNotifications(settings.isAllowNotifications())
                        .notifSound(settings.isNotifSound())
                        .notifVibration(settings.isNotifVibration())
                        .notifMessages(settings.isNotifMessages())
                        .notifGroups(settings.isNotifGroups())
                        .notifFriendRequests(settings.isNotifFriendRequests())
                        .dndSettings(settings.getDoNotDisturb() == null ? null :
                                com.bondhub.common.event.user.NotificationSettingsUpdatedEvent.DndSettings.builder()
                                        .dndEnabled(settings.getDoNotDisturb().isDndEnabled())
                                        .dndStartTime(settings.getDoNotDisturb().getDndStartTime())
                                        .dndEndTime(settings.getDoNotDisturb().getDndEndTime())
                                        .dndTimezone(settings.getDoNotDisturb().getDndTimezone())
                                        .activeDays(settings.getDoNotDisturb().getActiveDays() == null ? null :
                                            settings.getDoNotDisturb().getActiveDays().stream().map(Enum::name).toList())
                                        .build())
                        .build());
            });
        }

        outboxEventPublisher.saveAndPublish(userId, "UserSetting", EventType.NOTIFICATION_SETTINGS_UPDATED, event);
        log.info("Published NOTIFICATION_SETTINGS_UPDATED event for user: {}", userId);
    }

    @Override
    @Transactional
    public UserSettingResponse updateMessageSettingsSection(UserSetting.MessageSettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating messageSettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "messageSettings", request);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateCallSettingsSection(UserSetting.CallSettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating callSettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "callSettings", request);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updatePrivacySettingsSection(UserSetting.PrivacySettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating privacySettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "privacySettings", request);

        // Publish event for synchronization
        publishUserPrivacyChangedEvent(userId, request.isShowSeenStatus());

        return getMySettings();
    }

    private void publishUserPrivacyChangedEvent(String userId, boolean showSeenStatus) {
        UserPrivacyChangedEvent event = UserPrivacyChangedEvent.builder()
                .userId(userId)
                .showSeenStatus(showSeenStatus)
                .timestamp(System.currentTimeMillis())
                .build();

        outboxEventPublisher.saveAndPublish(userId, "UserSetting", EventType.USER_PRIVACY_CHANGED, event);
        log.info("Published USER_PRIVACY_CHANGED event via outbox for user: {}", userId);
    }

    @Override
    @Transactional
    public UserSettingResponse updateContactSettingsSection(UserSetting.ContactSettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating contactSettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "contactSettings", request);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateBackupRestoreSettingsSection(UserSetting.BackupRestoreSettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating backupRestoreSettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "backupRestoreSettings", request);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateAccountSecuritySettingsSection(UserSetting.AccountSecuritySettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating accountSecuritySettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "accountSecuritySettings", request);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateJournalSettingsSection(UserSetting.JournalSettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating journalSettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "journalSettings", request);
        return getMySettings();
    }

    @Override
    @Transactional
    public UserSettingResponse updateDataOnDeviceSettingsSection(UserSetting.DataOnDeviceSettings request) {
        String userId = getCurrentUserId(true);
        log.info("Updating dataOnDeviceSettings section for userId: {}", userId);
        userSettingRepository.updateSettingSection(userId, "dataOnDeviceSettings", request);
        return getMySettings();
    }

    @Override
    public UserSetting.LanguageAndInterface getLanguageAndInterfaceSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching language and interface settings for userId: {}", userId);
        UserSetting.LanguageAndInterface settings = userSettingRepository.getNestedSetting(
                userId,
                "languageAndInterface",
                UserSetting.LanguageAndInterface.class);
        applyCurrentDeviceLanguage(settings);
        return settings;
    }

    @Override
    public UserSetting.PrivacySettings getPrivacySettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching privacy settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "privacySettings", UserSetting.PrivacySettings.class);
    }

    @Override
    public UserSetting.NotificationSettings getNotificationSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching notification settings for userId: {}", userId);
        UserSetting.NotificationSettings settings = userSettingRepository.getNestedSetting(
            userId,
            "notificationSettings",
            UserSetting.NotificationSettings.class);
        applyCurrentDeviceNotificationPreference(settings);
        return settings;
    }

    @Override
    public UserSetting.MessageSettings getMessageSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching message settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "messageSettings", UserSetting.MessageSettings.class);
    }

    @Override
    public UserSetting.CallSettings getCallSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching call settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "callSettings", UserSetting.CallSettings.class);
    }

    @Override
    public UserSetting.ContactSettings getContactSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching contact settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "contactSettings", UserSetting.ContactSettings.class);
    }

    @Override
    public UserSetting.BackupRestoreSettings getBackupRestoreSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching backup and restore settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "backupRestoreSettings",
                UserSetting.BackupRestoreSettings.class);
    }

    @Override
    public UserSetting.AccountSecuritySettings getAccountSecuritySettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching account security settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "accountSecuritySettings",
                UserSetting.AccountSecuritySettings.class);
    }

    @Override
    public UserSetting.JournalSettings getJournalSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching journal settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "journalSettings", UserSetting.JournalSettings.class);
    }

    @Override
    public UserSetting.DataOnDeviceSettings getDataOnDeviceSettings() {
        String userId = getCurrentUserId(true);
        log.info("Fetching data on device settings for userId: {}", userId);
        return userSettingRepository.getNestedSetting(userId, "dataOnDeviceSettings",
                UserSetting.DataOnDeviceSettings.class);
    }

    @Override
    @Transactional
    public UserSettingResponse resetToDefaults() {
        String userId = getCurrentUserId(true);
        log.info("Resetting all settings to defaults for userId: {}", userId);

        UserSetting defaultSettings = new UserSetting();
        userSettingRepository.updateUserSetting(userId, defaultSettings);

        return UserSettingResponse.fromUserSetting(defaultSettings);
    }

    /**
     * Helper method to get current user ID from account ID
     */
    private String getCurrentUserId(boolean requireActive) {
        String accountId = securityUtil.getCurrentAccountId();
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (requireActive && !user.isActive()) {
            log.warn("Settings access denied for deactivated userId={} accountId={}", user.getId(), accountId);
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        String userId = user.getId();

        return userId;
    }

    private void applyCurrentDeviceLanguage(UserSetting.LanguageAndInterface settings) {
        if (settings == null || settings.getLanguageByDeviceId() == null || settings.getLanguageByDeviceId().isEmpty()) {
            return;
        }

        String deviceId = getCurrentDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }

        AppLanguage deviceLanguage = settings.getLanguageByDeviceId().get(deviceId);
        if (deviceLanguage != null) {
            settings.setLanguage(deviceLanguage);
        }
    }

    private void applyCurrentDeviceNotificationPreference(UserSetting.NotificationSettings settings) {
        if (settings == null || settings.getNotificationSettingsByDeviceId() == null
                || settings.getNotificationSettingsByDeviceId().isEmpty()) {
            return;
        }

        String deviceId = getCurrentDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }

        UserSetting.DeviceNotificationSettings deviceSettings = settings.getNotificationSettingsByDeviceId().get(deviceId);
        if (deviceSettings == null) {
            return;
        }

        settings.setAllowNotifications(deviceSettings.isAllowNotifications());
        settings.setNotifSound(deviceSettings.isNotifSound());
        settings.setNotifVibration(deviceSettings.isNotifVibration());
        settings.setNotifMessages(deviceSettings.isNotifMessages());
        settings.setNotifGroups(deviceSettings.isNotifGroups());
        settings.setNotifFriendRequests(deviceSettings.isNotifFriendRequests());
        if (deviceSettings.getDoNotDisturb() != null) {
            settings.setDoNotDisturb(deviceSettings.getDoNotDisturb());
        }
    }

    private UserSetting.DeviceNotificationSettings toDeviceNotificationSettings(
            UserSetting.NotificationSettings request,
            String deviceId
    ) {
        if (request == null) {
            return new UserSetting.DeviceNotificationSettings();
        }

        if (request.getNotificationSettingsByDeviceId() != null
                && !request.getNotificationSettingsByDeviceId().isEmpty()) {
            UserSetting.DeviceNotificationSettings byDeviceId = request.getNotificationSettingsByDeviceId().get(deviceId);
            if (byDeviceId != null) {
                return byDeviceId;
            }

            if (request.getNotificationSettingsByDeviceId().size() == 1) {
                return request.getNotificationSettingsByDeviceId().values().iterator().next();
            }
        }

        UserSetting.DeviceNotificationSettings deviceSettings = new UserSetting.DeviceNotificationSettings();
        deviceSettings.setAllowNotifications(request.isAllowNotifications());
        deviceSettings.setNotifSound(request.isNotifSound());
        deviceSettings.setNotifVibration(request.isNotifVibration());
        deviceSettings.setNotifMessages(request.isNotifMessages());
        deviceSettings.setNotifGroups(request.isNotifGroups());
        deviceSettings.setNotifFriendRequests(request.isNotifFriendRequests());
        if (request.getDoNotDisturb() != null) {
            deviceSettings.setDoNotDisturb(request.getDoNotDisturb());
        }
        return deviceSettings;
    }

    private String getCurrentDeviceId() {
        String deviceIdHeader = httpServletRequest.getHeader("X-Device-Id");
        if (deviceIdHeader != null && !deviceIdHeader.isBlank()) {
            return deviceIdHeader;
        }

        String sessionId = getCurrentSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        try {
            var response = authServiceClient.getDeviceBySessionId(sessionId);
            if (response != null && response.data() != null) {
                return response.data().deviceId();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve deviceId from sessionId {}: {}", sessionId, e.getMessage());
        }

        return null;
    }

    private String getCurrentSessionId() {
        String authHeader = httpServletRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);
        try {
            return jwtUtil.extractSessionId(token);
        } catch (Exception e) {
            log.warn("Failed to extract sessionId from bearer token: {}", e.getMessage());
            return null;
        }
    }
}
