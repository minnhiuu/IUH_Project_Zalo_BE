package com.bondhub.userservice.model;

import com.bondhub.userservice.model.enums.AppLanguage;
import com.bondhub.userservice.model.enums.AudioQuality;
import com.bondhub.userservice.model.enums.BackupFrequency;
import com.bondhub.userservice.model.enums.ChatFontSize;
import com.bondhub.userservice.model.enums.SettingScope;
import com.bondhub.userservice.model.enums.ThemeMode;
import com.bondhub.userservice.model.enums.VideoQuality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSetting {

    @Builder.Default
    private LanguageAndInterface languageAndInterface = new LanguageAndInterface();

    @Builder.Default
    private NotificationSettings notificationSettings = new NotificationSettings();

    @Builder.Default
    private MessageSettings messageSettings = new MessageSettings();

    @Builder.Default
    private CallSettings callSettings = new CallSettings();

    @Builder.Default
    private PrivacySettings privacySettings = new PrivacySettings();

    @Builder.Default
    private ContactSettings contactSettings = new ContactSettings();

    @Builder.Default
    private BackupRestoreSettings backupRestoreSettings = new BackupRestoreSettings();

    @Builder.Default
    private AccountSecuritySettings accountSecuritySettings = new AccountSecuritySettings();

    @Builder.Default
    private JournalSettings journalSettings = new JournalSettings();

    @Builder.Default
    private DataOnDeviceSettings dataOnDeviceSettings = new DataOnDeviceSettings();

    @Data
    public static class LanguageAndInterface {
        // Effective language for current device (resolved in service); also used as fallback.
        private AppLanguage language = AppLanguage.EN;
        // Per-device language preference keyed by deviceId.
        private Map<String, AppLanguage> languageByDeviceId = new HashMap<>();
        private ThemeMode themeMode = ThemeMode.SYSTEM;
        private double fontScale = 1.0;

    }

    @Data
    public static class NotificationSettings {
        private boolean allowNotifications = true;
        private boolean notifSound = true;
        private boolean notifVibration = true;
        private boolean notifMessages = true;
        private boolean notifGroups = true;
        private boolean notifFriendRequests = true;
        private DoNotDisturbSettings doNotDisturb = new DoNotDisturbSettings();
    }

    @Data
    public static class DoNotDisturbSettings {
        private boolean dndEnabled = false;
        private String dndStartTime = "22:00";
        private String dndEndTime = "07:00";
    }

    @Data
    public static class MessageSettings {
        private boolean messagePreview = true;
        private ChatFontSize fontSize = ChatFontSize.MEDIUM;
        private String chatTheme = "default";
        private boolean autoDownload = true;
        private boolean saveToLibrary = false;
        private boolean endToEndEncryption = true;
        private boolean showArchivedMessages = false;
    }

    @Data
    public static class CallSettings {
        private boolean allowCalls = true;
        private boolean allowVideoCalls = true;
        private AudioQuality audioQuality = AudioQuality.AUTOMATIC;
        private VideoQuality videoQuality = VideoQuality.HD;
        private String ringtone = "default";
        private boolean keepCallHistory = true;
    }

    @Data
    public static class PrivacySettings {
        private SettingScope birthdayVisibility = SettingScope.FRIENDS;
        private boolean showAccessStatus = true;
        private boolean showSeenStatus = true;
        private SettingScope allowMessaging = SettingScope.EVERYONE;
        private SettingScope allowCallsPrivacy = SettingScope.EVERYONE;
        private SettingScope allowViewAndCommentOnJournal = SettingScope.FRIENDS;
        private boolean blockUnknownUsers = false;
        private boolean friendSourceByPhone = true;
        private boolean friendSourceByQr = true;
        private List<String> utilityPermissions = new ArrayList<>();
        private List<String> blockedUserIds = new ArrayList<>();
    }

    @Data
    public static class ContactSettings {
        private boolean syncContacts = false;
        private boolean autoAddFromPhoneContacts = false;
    }

    @Data
    public static class BackupRestoreSettings {
        private boolean autoBackup = false;
        private boolean backupOverWifi = true;
        private BackupFrequency backupFrequency = BackupFrequency.WEEKLY;
        private LocalDateTime lastBackupAt = null;
        private BackupContentSettings backupContent = new BackupContentSettings();
    }

    @Data
    public static class BackupContentSettings {
        private boolean backupMessages = true;
        private boolean backupPhotos = true;
        private boolean backupVideos = false;
        private boolean backupFiles = false;
    }

    @Data
    public static class AccountSecuritySettings {
        private boolean twoFactorEnabled = false;
        private boolean lockAppEnabled = false;
        private boolean biometricsEnabled = false;
        private boolean logoutOtherDevicesOnPasswordChange = true;
    }

    @Data
    public static class JournalSettings {
        private String filterActivityType = "ALL";
        private String filterTimeRange = "ALL_TIME";
    }

    @Data
    public static class DataOnDeviceSettings {
        private boolean allowCellularMediaDownload = false;
        private int cacheCleanupThresholdMB = 500;
    }
}
