package com.bondhub.notificationservices.service.user.device;

import com.bondhub.notificationservices.dto.request.user.device.DeviceTokenRequest;
import com.bondhub.notificationservices.model.UserDevice;

import java.util.List;

public interface DeviceService {
    void registerDevice(DeviceTokenRequest request);
    void unregisterDevice(String token);
    List<UserDevice> getDevicesForUser(String userId);
}
