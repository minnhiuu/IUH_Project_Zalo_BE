package com.bondhub.userservice.service.settings;

import com.bondhub.common.dto.client.userservice.user.response.UserNotificationPreferenceResponse;
import com.bondhub.userservice.model.UserSetting;
import com.bondhub.userservice.repository.UserSettingRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSettingInternalServiceImpl implements UserSettingInternalService {

    UserSettingRepository userSettingRepository;

    @Override
    public UserNotificationPreferenceResponse getInternalNotificationPreferences(String userId) {
        log.info("Fetching internal notification preferences for userId: {}", userId);
        UserSetting userSetting = userSettingRepository.getUserSettingByUserId(userId);

        Map<String, String> deviceLocales = userSetting.getLanguageAndInterface().getLanguageByDeviceId() != null
                ? userSetting.getLanguageAndInterface().getLanguageByDeviceId().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().name().toLowerCase()
                ))
                : Map.of();

        Map<String, UserNotificationPreferenceResponse.DevicePreference> devicePreferences = 
                userSetting.getNotificationSettings().getNotificationSettingsByDeviceId() != null
                ? userSetting.getNotificationSettings().getNotificationSettingsByDeviceId().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> UserNotificationPreferenceResponse.DevicePreference.builder()
                                .allowNotifications(e.getValue().isAllowNotifications())
                                .notifMessages(e.getValue().isNotifMessages())
                                .notifGroups(e.getValue().isNotifGroups())
                                .notifFriendRequests(e.getValue().isNotifFriendRequests())
                                .build()
                ))
                : Map.of();

        return UserNotificationPreferenceResponse.builder()
                .userId(userId)
                .allowNotifications(userSetting.getNotificationSettings().isAllowNotifications())
                .language(userSetting.getLanguageAndInterface().getLanguage().name().toLowerCase())
                .languageByDeviceId(deviceLocales)
                .devicePreferences(devicePreferences)
                .build();
    }
}
