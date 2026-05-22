package com.bondhub.userservice.service.settings;

import com.bondhub.common.dto.client.userservice.user.response.UserSearchVisibilityResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;

import java.util.List;

public interface UserSettingInternalService {
    UserNotificationPreferenceResponse getInternalNotificationPreferences(String userId);

    List<UserSearchVisibilityResponse> getSearchVisibility(List<String> targetUserIds);
}
