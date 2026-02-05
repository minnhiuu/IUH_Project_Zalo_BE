package com.bondhub.userservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.dto.request.settings.*;
import com.bondhub.userservice.dto.response.settings.UserSettingResponse;
import com.bondhub.userservice.model.UserSetting;
import com.bondhub.userservice.service.settings.UserSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class UserSettingController {

    private final UserSettingService userSettingService;

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
            summary = "Update general settings",
            description = "Update general settings (language, friend visibility) for current user"
    )
    @PutMapping("/general")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateGeneralSettings(
            @Valid @RequestBody GeneralSettingsUpdateRequest request) {
        log.info("Updating general settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateGeneralSettings(request)));
    }

    @Operation(
            summary = "Update security settings",
            description = "Update security settings (two-factor authentication) for current user"
    )
    @PutMapping("/security")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateSecuritySettings(
            @Valid @RequestBody SecuritySettingsUpdateRequest request) {
        log.info("Updating security settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateSecuritySettings(request)));
    }

    @Operation(
            summary = "Update privacy settings",
            description = "Update privacy settings (visibility, who can contact, etc.) for current user"
    )
    @PutMapping("/privacy")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updatePrivacySettings(
            @Valid @RequestBody PrivacySettingsUpdateRequest request) {
        log.info("Updating privacy settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updatePrivacySettings(request)));
    }

    @Operation(
            summary = "Update sync settings",
            description = "Update synchronization settings for current user"
    )
    @PutMapping("/sync")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateSyncSettings(
            @Valid @RequestBody SyncSettingsUpdateRequest request) {
        log.info("Updating sync settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateSyncSettings(request)));
    }

    @Operation(
            summary = "Update appearance settings",
            description = "Update appearance settings (theme) for current user"
    )
    @PutMapping("/appearance")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateAppearanceSettings(
            @Valid @RequestBody AppearanceSettingsUpdateRequest request) {
        log.info("Updating appearance settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateAppearanceSettings(request)));
    }

    @Operation(
            summary = "Update message settings",
            description = "Update message-related settings (typing status, quick response, etc.) for current user"
    )
    @PutMapping("/message")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateMessageSettings(
            @Valid @RequestBody MessageSettingsUpdateRequest request) {
        log.info("Updating message settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateMessageSettings(request)));
    }

    @Operation(
            summary = "Update notification settings",
            description = "Update notification preferences for current user"
    )
    @PutMapping("/notification")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateNotificationSettings(
            @Valid @RequestBody NotificationSettingsUpdateRequest request) {
        log.info("Updating notification settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateNotificationSettings(request)));
    }

    @Operation(
            summary = "Update utilities settings",
            description = "Update utilities settings (sticker suggestions, etc.) for current user"
    )
    @PutMapping("/utilities")
    public ResponseEntity<ApiResponse<UserSettingResponse>> updateUtilitiesSettings(
            @Valid @RequestBody UtilitiesSettingsUpdateRequest request) {
        log.info("Updating utilities settings");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.updateUtilitiesSettings(request)));
    }

    @Operation(
            summary = "Get general settings",
            description = "Retrieve only general settings for current user"
    )
    @GetMapping("/me/general")
    public ResponseEntity<ApiResponse<UserSetting.GeneralSettings>> getGeneralSettings() {
        log.info("Getting general settings for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.getGeneralSettings()));
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
            summary = "Reset settings to defaults",
            description = "Reset all settings to their default values for current user"
    )
    @PostMapping("/me/reset")
    public ResponseEntity<ApiResponse<UserSettingResponse>> resetToDefaults() {
        log.info("Resetting settings to defaults for current user");
        return ResponseEntity.ok(ApiResponse.success(userSettingService.resetToDefaults()));
    }
}
