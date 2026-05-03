package com.bondhub.notificationservices.listener;

import com.bondhub.common.event.user.NotificationSettingsUpdatedEvent;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSettingEventListener {

    UserDeviceRepository userDeviceRepository;

    @KafkaListener(topics = "${kafka.topics.user-events.updated:user.updated}", groupId = "notification-service-settings-group")
    public void handleNotificationSettingsUpdated(NotificationSettingsUpdatedEvent settingsEvent, Acknowledgment ack) {
        log.info("Received NOTIFICATION_SETTINGS_UPDATED for user: {}", settingsEvent.getUserId());

        try {
            var devices = userDeviceRepository.findAllByUserId(settingsEvent.getUserId());
            for (var device : devices) {
                var deviceId = device.getDeviceId();
                
                // 1. Get settings for this specific device if available
                NotificationSettingsUpdatedEvent.DeviceSettings deviceSettings = null;
                if (settingsEvent.getDeviceSettingsMap() != null) {
                    deviceSettings = settingsEvent.getDeviceSettingsMap().get(deviceId);
                }

                // 2. Map settings to local UserDevice model
                if (deviceSettings != null) {
                    // Use device-specific settings
                    device.setAllowNotifications(deviceSettings.isAllowNotifications());
                    device.setNotifSound(deviceSettings.isNotifSound());
                    device.setNotifVibration(deviceSettings.isNotifVibration());
                    device.setNotifMessages(deviceSettings.isNotifMessages());
                    device.setNotifGroups(deviceSettings.isNotifGroups());
                    device.setNotifFriendRequests(deviceSettings.isNotifFriendRequests());
                    
                    if (deviceSettings.getDndSettings() != null) {
                        device.setDndEnabled(deviceSettings.getDndSettings().isDndEnabled());
                        device.setDndStartTime(deviceSettings.getDndSettings().getDndStartTime());
                        device.setDndEndTime(deviceSettings.getDndSettings().getDndEndTime());
                        device.setDndTimezone(deviceSettings.getDndSettings().getDndTimezone());
                        device.setActiveDays(deviceSettings.getDndSettings().getActiveDays() == null ? null :
                                deviceSettings.getDndSettings().getActiveDays().stream().map(DayOfWeek::valueOf).toList());
                    }
                } else if (settingsEvent.getGlobalSettings() != null) {
                    // Fallback to global settings
                    var global = settingsEvent.getGlobalSettings();
                    device.setAllowNotifications(global.isAllowNotifications());
                    device.setNotifSound(global.isNotifSound());
                    device.setNotifVibration(global.isNotifVibration());
                    device.setNotifMessages(global.isNotifMessages());
                    device.setNotifGroups(global.isNotifGroups());
                    device.setNotifFriendRequests(global.isNotifFriendRequests());
                    
                    if (global.getDndSettings() != null) {
                        device.setDndEnabled(global.getDndSettings().isDndEnabled());
                        device.setDndStartTime(global.getDndSettings().getDndStartTime());
                        device.setDndEndTime(global.getDndSettings().getDndEndTime());
                        device.setDndTimezone(global.getDndSettings().getDndTimezone());
                        device.setActiveDays(global.getDndSettings().getActiveDays() == null ? null :
                                global.getDndSettings().getActiveDays().stream().map(DayOfWeek::valueOf).toList());
                    }
                }
            }
            
            userDeviceRepository.saveAll(devices);
            log.info("Successfully synchronized notification settings for {} devices of user {}", devices.size(), settingsEvent.getUserId());
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error synchronizing notification settings for user {}: {}", settingsEvent.getUserId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }
}
