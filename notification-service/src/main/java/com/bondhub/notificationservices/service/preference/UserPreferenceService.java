package com.bondhub.notificationservices.service.preference;

import com.bondhub.notificationservices.enums.NotificationType;

public interface UserPreferenceService {

    boolean allow(String userId, NotificationType type);
}
