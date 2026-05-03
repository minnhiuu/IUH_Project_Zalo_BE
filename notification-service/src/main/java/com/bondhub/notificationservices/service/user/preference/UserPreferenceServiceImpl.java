package com.bondhub.notificationservices.service.user.preference;

import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.client.UserServiceClient;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.model.UserDevice;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserPreferenceServiceImpl implements UserPreferenceService {

    UserServiceClient userServiceClient;
    UserDeviceRepository userDeviceRepository;
    
    static final ZoneId DEFAULT_ZONE = ZoneId.of("GMT+7");

    @Override
    public UserNotificationPreferenceResponse getPreferences(String userId) {
        try {
            var response = userServiceClient.getNotificationPreferences(userId);
            if (response.getBody() != null && response.getBody().data() != null) {
                return response.getBody().data();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch fresh preferences for user {}, falling back to local snapshots.", userId);
        }
        return null;
    }

    @Override
    public boolean allow(UserNotificationPreferenceResponse data, String deviceId, NotificationType type) {
        // 1. Try local snapshot if deviceId is provided
        if (deviceId != null) {
            var deviceOpt = userDeviceRepository.findByDeviceId(deviceId);
            if (deviceOpt.isPresent()) {
                UserDevice device = deviceOpt.get();
                if (!device.isAllowNotifications()) return false;
                
                // 1.1 Check DND Schedule (Time + Day)
                if (device.isDndEnabled()) {
                    String tz = device.getDndTimezone() != null ? device.getDndTimezone() : "GMT+7";
                    ZoneId deviceZone = ZoneId.of(tz);
                    LocalDateTime now = LocalDateTime.now(deviceZone);
                    DayOfWeek currentDay = now.getDayOfWeek();
                    
                    if (device.getActiveDays() == null || device.getActiveDays().contains(currentDay)) {
                        if (isWithinTimeRange(now.toLocalTime(), device.getDndStartTime(), device.getDndEndTime())) {
                            log.info("Notification silenced by DND schedule for device: {}", deviceId);
                            return false;
                        }
                    }
                }
                
                return switch (type) {
                    case FRIEND_REQUEST -> device.isNotifFriendRequests();
                    case MESSAGE_DIRECT -> device.isNotifMessages();
                    case MESSAGE_GROUP -> device.isNotifGroups();
                    default -> true;
                };
            }
        }

        // 2. Fallback to passed 'data' (from Feign) if snapshot not available
        if (data == null) return true;

        if (deviceId != null && data.getDevicePreferences() != null) {
            var devicePref = data.getDevicePreferences().get(deviceId);
            if (devicePref != null) {
                if (!devicePref.isAllowNotifications()) return false;
                return switch (type) {
                    case FRIEND_REQUEST -> devicePref.isNotifFriendRequests();
                    case MESSAGE_DIRECT -> devicePref.isNotifMessages();
                    default -> true;
                };
            }
        }

        return data.isAllowNotifications();
    }

    private boolean isWithinTimeRange(LocalTime now, String startTimeStr, String endTimeStr) {
        if (startTimeStr == null || endTimeStr == null) return false;
        
        try {
            LocalTime start = LocalTime.parse(startTimeStr);
            LocalTime end = LocalTime.parse(endTimeStr);
            
            if (start.isBefore(end)) {
                return !now.isBefore(start) && !now.isAfter(end);
            } else {
                // Crosses midnight (e.g., 22:00 to 07:00)
                return !now.isBefore(start) || !now.isAfter(end);
            }
        } catch (Exception e) {
            log.warn("Failed to parse DND time range: {} - {}", startTimeStr, endTimeStr);
            return false;
        }
    }
}
