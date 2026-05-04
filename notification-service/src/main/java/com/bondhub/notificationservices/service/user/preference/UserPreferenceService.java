package com.bondhub.notificationservices.service.user.preference;

import com.bondhub.common.enums.NotificationType;

public interface UserPreferenceService {

    boolean recipientExists(String userId);

    String getLocale(String userId);

    boolean allow(String userId, NotificationType type);

    boolean shouldSilenceByDnd(String userId);
}
