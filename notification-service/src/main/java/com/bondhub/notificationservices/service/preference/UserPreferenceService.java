package com.bondhub.notificationservices.service.preference;

import com.bondhub.common.enums.NotificationType;

public interface UserPreferenceService {

    boolean allow(String userId, NotificationType type);

    String getLocale(String userId);
}
