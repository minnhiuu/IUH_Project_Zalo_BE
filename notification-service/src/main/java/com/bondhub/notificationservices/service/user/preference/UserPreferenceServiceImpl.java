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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserPreferenceServiceImpl implements UserPreferenceService {

    UserServiceClient userServiceClient;
    UserDeviceRepository userDeviceRepository;

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

                // DND check has been moved to shouldSilenceByDnd()
                // to cleanly separate DISABLED_BY_USER vs SILENCED_BY_DND

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

    @Override
    public boolean shouldSilenceByDnd(String userId, UserNotificationPreferenceResponse data, String deviceId) {
        // 1. Check specific device-level DND from local snapshot
        if (deviceId != null) {
            var deviceOpt = userDeviceRepository.findByDeviceId(deviceId);

            if (deviceOpt.isPresent()) {
                UserDevice device = deviceOpt.get();
                return isDeviceInDnd(device);
            }
        }

        // 2. If no deviceId specified, check if ANY device for this user is in DND
        if (userId != null) {
            List<UserDevice> devices = userDeviceRepository.findByUserId(userId);
            for (UserDevice device : devices) {
                if (isDeviceInDnd(device)) {
                    return true;
                }
            }
        }

        // No global DND info available in UserNotificationPreferenceResponse,
        // DND is managed per-device via UserDevice snapshots synced from user-service.
        return false;
    }

    private boolean isDeviceInDnd(UserDevice device) {
        if (!device.isDndEnabled()) {
            return false;
        }

        if (device.getDndStartTime() == null || device.getDndEndTime() == null) {
            return false;
        }

        try {
            String timezone = device.getDndTimezone() != null
                    ? device.getDndTimezone()
                    : "Asia/Ho_Chi_Minh";

            // Normalize "GMT+X" or "GMT-X" to "GMT+0X:00" to avoid DateTimeException
            if (timezone.startsWith("GMT") && (timezone.contains("+") || timezone.contains("-"))) {
                String prefix = timezone.substring(0, 4); // "GMT+" or "GMT-"
                String offset = timezone.substring(4);   // "7" or "7:00"
                if (!offset.contains(":")) {
                    if (offset.length() == 1) offset = "0" + offset + ":00";
                    else if (offset.length() == 2) offset = offset + ":00";
                } else {
                    String[] parts = offset.split(":");
                    if (parts[0].length() == 1) offset = "0" + offset;
                }
                timezone = prefix + offset;
            }

            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);

            LocalTime now = nowZoned.toLocalTime();
            LocalTime start = LocalTime.parse(device.getDndStartTime());
            LocalTime end = LocalTime.parse(device.getDndEndTime());

            return isWithinDndWindow(
                    nowZoned,
                    now,
                    start,
                    end,
                    device.getActiveDays()
            );
        } catch (Exception e) {
            log.warn("Failed to check device DND: deviceId={}, error={}",
                    device.getDeviceId(), e.getMessage());
            return false;
        }
    }

    private boolean isWithinDndWindow(
            ZonedDateTime nowZoned,
            LocalTime now,
            LocalTime start,
            LocalTime end,
            List<DayOfWeek> activeDays
    ) {
        boolean hasActiveDays = activeDays != null && !activeDays.isEmpty();

        // Case 1: Same day window, e.g. 09:00 -> 17:00
        if (start.isBefore(end)) {
            if (hasActiveDays && !activeDays.contains(nowZoned.getDayOfWeek())) {
                return false;
            }

            return !now.isBefore(start) && now.isBefore(end);
        }

        // Case 2: Crosses midnight, e.g. 23:00 -> 07:00
        boolean inNightPart = !now.isBefore(start);
        boolean inMorningPart = now.isBefore(end);

        if (inNightPart) {
            if (hasActiveDays && !activeDays.contains(nowZoned.getDayOfWeek())) {
                return false;
            }

            return true;
        }

        if (inMorningPart) {
            DayOfWeek previousDay = nowZoned.minusDays(1).getDayOfWeek();

            if (hasActiveDays && !activeDays.contains(previousDay)) {
                return false;
            }

            return true;
        }

        return false;
    }
}
