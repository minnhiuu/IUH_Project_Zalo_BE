package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.dto.response.settings.UserSettingResponse;
import com.bondhub.userservice.model.UserSetting;
import com.bondhub.userservice.service.settings.UserSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing user settings using MongoDB dot notation
 * to efficiently update settings without loading entire User documents
 */
@RestController
@RequestMapping("/users/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Settings", description = "APIs for managing user settings with efficient MongoDB dot notation updates")
@SecurityRequirement(name = "bearer-key")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSettingController {

    UserSettingService userSettingService;

    @Operation(
            summary = "Get my settings",
            description = "Retrieve all settings for the currently authenticated user"
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserSettingResponse>> getMySettings() {
        log.info("Getting settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getMySettings()));
    }

    @Operation(
            summary = "Get settings by user ID",
            description = "Retrieve all settings for a specific user (admin only)"
    )
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserSettingResponse>> getSettingsByUserId(@PathVariable String userId) {
        log.info("Getting settings for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getSettingsByUserId(userId)));
    }

    @Operation(
            summary = "Update language and interface settings section",
            description = "Replace the language and interface settings section for current user"
    )
    @PutMapping("/me/language-and-interface")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateLanguageAndInterfaceSettings(
            @RequestBody UserSetting.LanguageAndInterface request) {
        log.info("Updating language and interface settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateLanguageAndInterfaceSettings(request)));
    }

    @Operation(
            summary = "Update notification settings section",
            description = "Replace the notification settings section for current user"
    )
    @PutMapping("/me/notification")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateNotificationSettingsSection(
            @RequestBody UserSetting.NotificationSettings request) {
        log.info("Updating notification settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateNotificationSettingsSection(request)));
    }

    @Operation(
            summary = "Update message settings section",
            description = "Replace the message settings section for current user"
    )
    @PutMapping("/me/message")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateMessageSettingsSection(
            @RequestBody UserSetting.MessageSettings request) {
        log.info("Updating message settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateMessageSettingsSection(request)));
    }

    @Operation(
            summary = "Update call settings section",
            description = "Replace the call settings section for current user"
    )
    @PutMapping("/me/call")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateCallSettingsSection(
            @RequestBody UserSetting.CallSettings request) {
        log.info("Updating call settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateCallSettingsSection(request)));
    }

    @Operation(
            summary = "Update privacy settings section",
            description = "Replace the privacy settings section for current user"
    )
    @PutMapping("/me/privacy")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updatePrivacySettingsSection(
            @RequestBody UserSetting.PrivacySettings request) {
        log.info("Updating privacy settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updatePrivacySettingsSection(request)));
    }

    @Operation(
            summary = "Update contact settings section",
            description = "Replace the contact settings section for current user"
    )
    @PutMapping("/me/contact")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateContactSettingsSection(
            @RequestBody UserSetting.ContactSettings request) {
        log.info("Updating contact settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateContactSettingsSection(request)));
    }

    @Operation(
            summary = "Update backup and restore settings section",
            description = "Replace the backup and restore settings section for current user"
    )
    @PutMapping("/me/backup-restore")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateBackupRestoreSettingsSection(
            @RequestBody UserSetting.BackupRestoreSettings request) {
        log.info("Updating backup and restore settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateBackupRestoreSettingsSection(request)));
    }

    @Operation(
            summary = "Update account security settings section",
            description = "Replace the account security settings section for current user"
    )
    @PutMapping("/me/account-security")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateAccountSecuritySettingsSection(
            @RequestBody UserSetting.AccountSecuritySettings request) {
        log.info("Updating account security settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateAccountSecuritySettingsSection(request)));
    }

    @Operation(
            summary = "Update journal settings section",
            description = "Replace the journal settings section for current user"
    )
    @PutMapping("/me/journal")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateJournalSettingsSection(
            @RequestBody UserSetting.JournalSettings request) {
        log.info("Updating journal settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateJournalSettingsSection(request)));
    }

    @Operation(
            summary = "Update data on device settings section",
            description = "Replace the data on device settings section for current user"
    )
    @PutMapping("/me/data-on-device")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateDataOnDeviceSettingsSection(
            @RequestBody UserSetting.DataOnDeviceSettings request) {
        log.info("Updating data on device settings section");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateDataOnDeviceSettingsSection(request)));
    }

    @Operation(
            summary = "Get language and interface settings",
            description = "Retrieve only language and interface settings for current user"
    )
    @GetMapping("/me/language-and-interface")
    public ResponseEntity<ApiResponse<UserSetting.LanguageAndInterface>> getLanguageAndInterfaceSettings() {
        log.info("Getting language and interface settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getLanguageAndInterfaceSettings()));
    }

    @Operation(
            summary = "Get privacy settings",
            description = "Retrieve only privacy settings for current user"
    )
    @GetMapping("/me/privacy")
    public ResponseEntity<ApiResponse<UserSetting.PrivacySettings>> getPrivacySettings() {
        log.info("Getting privacy settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getPrivacySettings()));
    }

    @Operation(
            summary = "Get notification settings",
            description = "Retrieve only notification settings for current user"
    )
    @GetMapping("/me/notification")
    public ResponseEntity<ApiResponse<UserSetting.NotificationSettings>> getNotificationSettings() {
        log.info("Getting notification settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getNotificationSettings()));
    }

    @Operation(
            summary = "Get message settings",
            description = "Retrieve only message settings for current user"
    )
    @GetMapping("/me/message")
    public ResponseEntity<ApiResponse<UserSetting.MessageSettings>> getMessageSettings() {
        log.info("Getting message settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getMessageSettings()));
    }

    @Operation(
            summary = "Get call settings",
            description = "Retrieve only call settings for current user"
    )
    @GetMapping("/me/call")
    public ResponseEntity<ApiResponse<UserSetting.CallSettings>> getCallSettings() {
        log.info("Getting call settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getCallSettings()));
    }

    @Operation(
            summary = "Get contact settings",
            description = "Retrieve only contact settings for current user"
    )
    @GetMapping("/me/contact")
    public ResponseEntity<ApiResponse<UserSetting.ContactSettings>> getContactSettings() {
        log.info("Getting contact settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getContactSettings()));
    }

    @Operation(
            summary = "Get backup and restore settings",
            description = "Retrieve only backup and restore settings for current user"
    )
    @GetMapping("/me/backup-restore")
    public ResponseEntity<ApiResponse<UserSetting.BackupRestoreSettings>> getBackupRestoreSettings() {
        log.info("Getting backup and restore settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getBackupRestoreSettings()));
    }

    @Operation(
            summary = "Get account security settings",
            description = "Retrieve only account security settings for current user"
    )
    @GetMapping("/me/account-security")
    public ResponseEntity<ApiResponse<UserSetting.AccountSecuritySettings>> getAccountSecuritySettings() {
        log.info("Getting account security settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getAccountSecuritySettings()));
    }

    @Operation(
            summary = "Get journal settings",
            description = "Retrieve only journal settings for current user"
    )
    @GetMapping("/me/journal")
    public ResponseEntity<ApiResponse<UserSetting.JournalSettings>> getJournalSettings() {
        log.info("Getting journal settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getJournalSettings()));
    }

    @Operation(
            summary = "Get data on device settings",
            description = "Retrieve only data on device settings for current user"
    )
    @GetMapping("/me/data-on-device")
    public ResponseEntity<ApiResponse<UserSetting.DataOnDeviceSettings>> getDataOnDeviceSettings() {
        log.info("Getting data on device settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getDataOnDeviceSettings()));
    }

    @Operation(
            summary = "Reset settings to defaults",
            description = "Reset all settings to their default values for current user"
    )
    @PostMapping("/me/reset")
    public ResponseEntity<ApiResponse<UserSettingResponse>> resetToDefaults() {
        log.info("Resetting settings to defaults for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.resetToDefaults()));
    }
}
