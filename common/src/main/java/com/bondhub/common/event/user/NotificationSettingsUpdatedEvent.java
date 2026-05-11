package com.bondhub.common.event.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsUpdatedEvent {
    private String userId;
    private String deviceId; // Optional: if update is for specific device
    private GlobalSettings globalSettings;
    private Map<String, DeviceSettings> deviceSettingsMap;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlobalSettings {
        private boolean allowNotifications;
        private boolean notifSound;
        private boolean notifVibration;
        private boolean notifMessages;
        private boolean notifGroups;
        private boolean notifFriendRequests;
        private DndSettings dndSettings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceSettings {
        private boolean allowNotifications;
        private boolean notifSound;
        private boolean notifVibration;
        private boolean notifMessages;
        private boolean notifGroups;
        private boolean notifFriendRequests;
        private DndSettings dndSettings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DndSettings {
        private boolean dndEnabled;
        private String dndStartTime;
        private String dndEndTime;
        private String dndTimezone;
        private List<String> activeDays;
    }
}
