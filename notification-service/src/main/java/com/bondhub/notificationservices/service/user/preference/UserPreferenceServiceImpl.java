package com.bondhub.notificationservices.service.user.preference;

import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.client.UserServiceClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserPreferenceServiceImpl implements UserPreferenceService {

    UserServiceClient userServiceClient;

    @Override
    public UserNotificationPreferenceResponse getPreferences(String userId) {
        try {
            var response = userServiceClient.getNotificationPreferences(userId);
            if (response.getBody() != null && response.getBody().data() != null) {
                return response.getBody().data();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    @Override
    public boolean allow(UserNotificationPreferenceResponse data, String deviceId, NotificationType type) {
        if (data == null) return true;

        if (deviceId != null) {
            if (data.getDevicePreferences() != null) {
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

        if (data.isAllowNotifications()) return true;

        if (data.getDevicePreferences() != null) {
            return data.getDevicePreferences().values().stream()
                    .anyMatch(UserNotificationPreferenceResponse.DevicePreference::isAllowNotifications);
        }

        return false;
    }
}
