package com.bondhub.notificationservices.service.dnd;

import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.dto.dnd.DndSummaryItem;
import com.bondhub.notificationservices.enums.DndMissedStatus;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.model.DndMissedNotification;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.DndMissedNotificationRepository;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.delivery.NotificationContentBuilder;
import com.bondhub.notificationservices.service.push.FcmService;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
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
    NotificationContentBuilder contentBuilder;
    NotificationTemplateService templateService;
    FcmService fcmService;

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

        if (grouped.isEmpty()) return;

        // 1. Get all user devices
        List<UserDevice> devices = userDeviceRepository.findByUserId(userId);
        if (devices.isEmpty()) {
            log.info("[DND Summary] User {} has no devices, skipping push.", userId);
            markAsSummarized(missed);
            return;
        }

        // 2. Cache rendered content per locale to avoid redundant processing
        Map<String, NotificationContentBuilder.RenderedContent> renderedCache = new HashMap<>();
        int totalCount = missed.size();

        int sentCount = 0;
        int skippedDisabledCount = 0;

        for (UserDevice device : devices) {
            if (!device.isAllowNotifications()) {
                skippedDisabledCount++;
                log.info("[DND Summary] Skip device because notifications disabled: user={}, device={}, platform={}",
                        userId,
                        device.getDeviceId(),
                        device.getPlatform());
                continue;
            }

            String locale = device.getLocale() != null ? device.getLocale() : "vi";

            NotificationContentBuilder.RenderedContent rendered = renderedCache.computeIfAbsent(locale, loc -> {
                List<DndSummaryItem> summaryItems = buildSummaryItems(grouped, loc);
                String summaryText = buildSummaryText(summaryItems);

                Notification mockNoti = Notification.builder()
                        .userId(userId)
                        .type(NotificationType.DND_SUMMARY)
                        .payload(Map.of(
                                "count", totalCount,
                                "totalCount", totalCount,
                                "summaryText", summaryText
                        ))
                        .build();

                return contentBuilder.render(mockNoti, NotificationChannel.FCM, loc);
            });

            // 3. Send to this specific device
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("totalCount", totalCount);
            metadata.put("actorAvatar", frontendUrl + (frontendUrl.endsWith("/") ? "" : "/") + "images/logo.jpg");

            log.info("[DND Summary] Sending summary push: user={}, device={}, platform={}, totalCount={}",
                    userId,
                    device.getDeviceId(),
                    device.getPlatform(),
                    totalCount);

            fcmService.sendPush(null, device, rendered.title(), rendered.body(), NotificationType.DND_SUMMARY.name(), metadata);
            sentCount++;
        }

        log.info("[DND Summary] Push summary result: user={}, devices={}, sent={}, skippedDisabled={}",
                userId,
                devices.size(),
                sentCount,
                skippedDisabledCount);

        // 4. Update status
        markAsSummarized(missed);
    }

    private void markAsSummarized(List<DndMissedNotification> missed) {
        LocalDateTime now = LocalDateTime.now();
        for (DndMissedNotification item : missed) {
            item.setStatus(DndMissedStatus.SUMMARIZED);
            item.setSummarizedAt(now);
        }
        missedRepository.saveAll(missed);
        log.info("[DND Summary] Marked {} notifications as summarized", missed.size());
    }



    private String buildSummaryText(List<DndSummaryItem> items) {
        if (items.isEmpty()) return "";
        return items.stream()
                .map(DndSummaryItem::body)
                .collect(Collectors.joining(", "));
    }

    private List<DndSummaryItem> buildSummaryItems(Map<String, List<DndMissedNotification>> grouped, String locale) {
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
}
