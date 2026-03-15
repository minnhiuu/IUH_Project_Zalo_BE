package com.bondhub.notificationservices.service.user.device;

import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.notificationservices.dto.request.user.device.DeviceTokenRequest;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondhub.notificationservices.enums.Platform;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    UserDeviceRepository userDeviceRepository;
    SecurityUtil securityUtil;

    @Override
    @Transactional
    public void registerDevice(DeviceTokenRequest request) {
        String userId = securityUtil.getCurrentUserId();
        String token = request.token();
        Platform platform = request.platform();
        String deviceId = request.deviceId();
        String locale = request.locale() != null ? request.locale() : "vi";

        log.info("[FCM] Registering device: user={}, platform={}, deviceId={}, locale={}, token={}", 
                userId, platform, deviceId, locale, token.substring(0, Math.min(token.length(), 10)));

        try {
            userDeviceRepository.deleteByFcmTokenAndUserIdNot(token, userId);

            List<UserDevice> existingDevices = userDeviceRepository.findByUserId(userId);
            UserDevice existing = existingDevices.stream()
                    .filter(d -> d.getFcmToken().equals(token))
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                if (platform == Platform.WEB) {
                    userDeviceRepository.deleteByUserIdAndPlatformIn(userId, List.of(Platform.WEB));
                }

                UserDevice newDevice = UserDevice.builder()
                        .userId(userId)
                        .fcmToken(token)
                        .platform(platform)
                        .deviceId(deviceId)
                        .locale(locale)
                        .build();
                userDeviceRepository.save(newDevice);
                log.info("[FCM] New device saved for user: {}", userId);
            } else {
                boolean updated = false;
                if (deviceId != null && !deviceId.equals(existing.getDeviceId())) {
                    log.info("[FCM] Updating deviceId for user {}: old={}, new={}", userId, existing.getDeviceId(), deviceId);
                    existing.setDeviceId(deviceId);
                    updated = true;
                }
                if (!locale.equals(existing.getLocale())) {
                    log.info("[FCM] Updating locale for user {}: old={}, new={} (deviceId={})", 
                            userId, existing.getLocale(), locale, deviceId);
                    existing.setLocale(locale);
                    updated = true;
                }

                if (updated) {
                    userDeviceRepository.save(existing);
                    log.info("[FCM] Updated device info for user: {}", userId);
                } else {
                    log.info("[FCM] Device already registered with same locale ({}) for user: {}, skipping save", locale, userId);
                }
            }
        } catch (Exception e) {
            log.error("[FCM] Error registering device: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public void unregisterDevice(String token) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Unregistering device for user: {}", currentUserId);
        userDeviceRepository.deleteByUserIdAndFcmToken(currentUserId, token);
    }

    @Override
    public List<UserDevice> getDevicesForUser(String userId) {
        return userDeviceRepository.findByUserId(userId);
    }
}
