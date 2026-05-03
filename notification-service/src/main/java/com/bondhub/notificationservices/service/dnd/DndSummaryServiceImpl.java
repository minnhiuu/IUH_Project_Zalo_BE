package com.bondhub.notificationservices.service.dnd;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.dto.dnd.DndSummaryItem;
import com.bondhub.notificationservices.enums.DndMissedStatus;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.enums.Platform;
import com.bondhub.notificationservices.model.DndMissedNotification;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.DndMissedNotificationRepository;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.delivery.NotificationStrategyHelper;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import com.google.firebase.messaging.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DndSummaryServiceImpl implements DndSummaryService {

    DndMissedNotificationRepository missedRepository;
    UserDeviceRepository userDeviceRepository;
    UserPreferenceService userPreferenceService;
    NotificationStrategyHelper strategyHelper;
    NotificationTemplateService templateService;

    @NonFinal
    @Value("${bondhub.frontend-url}")
    String frontendUrl;

    @Override
    public void sendSummaryForUser(String userId) {
        log.info("[DND Summary] Processing summary for user: {}", userId);

        List<DndMissedNotification> missed = missedRepository.findByUserIdAndStatus(
                userId,
                DndMissedStatus.PENDING
        );

        if (missed.isEmpty()) {
            log.info("[DND Summary] No pending missed notifications for user: {}", userId);
            return;
        }

        Map<String, List<DndMissedNotification>> grouped = missed.stream()
                .collect(Collectors.groupingBy(DndMissedNotification::getSummaryGroupKey));

        if (grouped.isEmpty()) {
            return;
        }

        // 1. Build summary items
        var prefs = userPreferenceService.getPreferences(userId);
        String locale = (prefs != null) ? prefs.getLanguage() : "vi";
        if (locale == null) locale = "vi";

        List<DndSummaryItem> summaryItems = buildSummaryItems(grouped, locale);

        int totalCount = summaryItems.stream()
                .mapToInt(DndSummaryItem::count)
                .sum();

        // 2. Build detailed summary text
        String summaryText = buildSummaryText(summaryItems);

        // 3. Render localized content from DATABASE Template (which is now force-updated by DataInitializer)
        Notification mockNoti = Notification.builder()
                .userId(userId)
                .type(NotificationType.DND_SUMMARY)
                .payload(Map.of(
                        "count", totalCount,
                        "totalCount", totalCount,
                        "summaryText", summaryText
                ))
                .build();
        
        var rendered = strategyHelper.render(mockNoti, NotificationChannel.FCM, locale);

        log.info("[DND Summary] Pushing summary to user {}: {}", userId, rendered.body());
        sendSummaryPush(userId, rendered.title(), rendered.body(), totalCount);

        LocalDateTime now = LocalDateTime.now();
        for (DndMissedNotification item : missed) {
            item.setStatus(DndMissedStatus.SUMMARIZED);
            item.setSummarizedAt(now);
        }
        missedRepository.saveAll(missed);
    }

    private String buildSummaryText(List<DndSummaryItem> items) {
        if (items.isEmpty()) return "";
        return items.stream()
                .map(DndSummaryItem::body)
                .collect(Collectors.joining(", "));
    }

    private List<DndSummaryItem> buildSummaryItems(
            Map<String, List<DndMissedNotification>> grouped,
            String locale
    ) {
        List<DndSummaryItem> items = new ArrayList<>();
        boolean isVi = "vi".equalsIgnoreCase(locale);

        int messageCount = 0;
        int conversationCount = 0;
        int friendRequestCount = 0;
        int postInteractionCount = 0;
        int postCount = 0;
        int otherCount = 0;

        for (Map.Entry<String, List<DndMissedNotification>> entry : grouped.entrySet()) {
            String groupKey = entry.getKey();
            int count = entry.getValue().size();

            if (groupKey.startsWith("MESSAGE:")) {
                messageCount += count;
                conversationCount++;
            } else if (groupKey.equals("FRIEND_REQUEST")) {
                friendRequestCount += count;
            } else if (groupKey.startsWith("POST:")) {
                postInteractionCount += count;
                postCount++;
            } else {
                otherCount += count;
            }
        }

        if (messageCount > 0) {
            String fragment = renderFragment(NotificationType.DND_SUMMARY_MESSAGE, locale, Map.of(
                    "messageCount", messageCount,
                    "conversationCount", conversationCount
            ));
            items.add(DndSummaryItem.builder()
                    .count(messageCount)
                    .title(isVi ? "Tin nhắn mới" : "New messages")
                    .body(fragment)
                    .build());
        }

        if (friendRequestCount > 0) {
            String fragment = renderFragment(NotificationType.DND_SUMMARY_FRIEND, locale, Map.of(
                    "count", friendRequestCount
            ));
            items.add(DndSummaryItem.builder()
                    .count(friendRequestCount)
                    .title(isVi ? "Lời mời kết bạn" : "Friend requests")
                    .body(fragment)
                    .build());
        }

        if (postInteractionCount > 0) {
            String fragment = renderFragment(NotificationType.DND_SUMMARY_POST, locale, Map.of(
                    "interactionCount", postInteractionCount,
                    "postCount", postCount
            ));
            items.add(DndSummaryItem.builder()
                    .count(postInteractionCount)
                    .title(isVi ? "Tương tác bài viết" : "Post interactions")
                    .body(fragment)
                    .build());
        }

        if (otherCount > 0) {
            String fragment = renderFragment(NotificationType.DND_SUMMARY_OTHER, locale, Map.of(
                    "count", otherCount
            ));
            items.add(DndSummaryItem.builder()
                    .count(otherCount)
                    .title(isVi ? "Thông báo khác" : "Other notifications")
                    .body(fragment)
                    .build());
        }

        return items;
    }

    private String renderFragment(NotificationType type, String locale, Map<String, Object> data) {
        try {
            var template = templateService.getTemplate(type, NotificationChannel.FCM, locale);
            return templateService.render(template.bodyTemplate(), data);
        } catch (Exception e) {
            log.warn("[DND Summary] Failed to render fragment {}: {}", type, e.getMessage());
            return "";
        }
    }

    private void sendSummaryPush(String userId, String title, String body, int totalCount) {
        List<UserDevice> devices = userDeviceRepository.findByUserId(userId);
        if (devices.isEmpty()) return;

        String url = frontendUrl;
        if (!url.endsWith("/")) url += "/";
        String clickUrl = url + "?noti_open=true";
        String iconUrl = url + "images/logo.jpg";

        for (UserDevice device : devices) {
            if (!device.isAllowNotifications()) continue;

            Map<String, String> dataPayload = new HashMap<>();
            // Use SYSTEM_INFO to bypass FE override
            dataPayload.put("type", "SYSTEM_INFO"); 
            dataPayload.put("title", title);
            dataPayload.put("body", body);
            dataPayload.put("totalCount", String.valueOf(totalCount));
            dataPayload.put("url", clickUrl);
            dataPayload.put("actorAvatar", iconUrl);

            if (device.getPlatform() == Platform.ANDROID) {
                dataPayload.put("customTitle", title);
                dataPayload.put("customBody", body);
                dataPayload.remove("title");
                dataPayload.remove("body");
            }

            Message.Builder builder = Message.builder()
                    .setToken(device.getFcmToken())
                    .putAllData(dataPayload);

            if (device.getPlatform() == Platform.WEB) {
                builder.setWebpushConfig(WebpushConfig.builder()
                        .setFcmOptions(WebpushFcmOptions.withLink(clickUrl))
                        .build());
            }

            try {
                FirebaseMessaging.getInstance().send(builder.build());
                log.info("[DND Summary] Push sent: user={}, device={}", userId, device.getDeviceId());
            } catch (Exception e) {
                log.error("[DND Summary] Push failed: user={}, error={}", userId, e.getMessage());
            }
        }
    }
}
