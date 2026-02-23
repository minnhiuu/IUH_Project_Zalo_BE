package com.bondhub.notificationservices.service.frequency;

import com.bondhub.notificationservices.enums.NotificationType;

public interface FrequencyControlService {

    boolean allow(String userId, NotificationType type);
}
