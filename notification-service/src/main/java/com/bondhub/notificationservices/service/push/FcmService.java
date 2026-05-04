package com.bondhub.notificationservices.service.push;

import com.bondhub.notificationservices.model.UserDevice;
import java.util.Map;

public interface FcmService {
    void sendPush(UserDevice device, String title, String body, String type, Map<String, Object> metadata);
}
