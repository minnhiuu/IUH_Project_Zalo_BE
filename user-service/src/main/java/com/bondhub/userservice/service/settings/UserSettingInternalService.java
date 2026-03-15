package com.bondhub.userservice.service.settings;

import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;

public interface UserSettingInternalService {
    UserNotificationPreferenceResponse getInternalNotificationPreferences(String userId);
}
