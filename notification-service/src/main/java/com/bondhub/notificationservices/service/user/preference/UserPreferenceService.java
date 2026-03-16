package com.bondhub.notificationservices.service.user.preference;

import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;
import com.bondhub.common.enums.NotificationType;

public interface UserPreferenceService {

    UserNotificationPreferenceResponse getPreferences(String userId);

    boolean allow(UserNotificationPreferenceResponse prefs, String deviceId, NotificationType type);
}
