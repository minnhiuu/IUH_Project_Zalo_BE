package com.bondhub.notificationservices.service.preference;

import com.bondhub.common.enums.NotificationType;
import org.springframework.stereotype.Service;

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    @Override
    public boolean allow(String userId, NotificationType type) {
        return true;
    }

    @Override
    public String getLocale(String userId) {
        return "vi";
    }
}
