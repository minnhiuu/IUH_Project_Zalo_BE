package com.bondhub.notificationservices.config;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.NotificationTemplate;
import com.bondhub.notificationservices.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    final NotificationTemplateRepository templateRepository;
    final MongoTemplate mongoTemplate;

    private static final String TARGET_USER_ID = "698bf759341d252b21b775ee";
    private static final List<String> ACTOR_IDS = List.of(
            "698be9ef85474313b23f48bf", "698be9ef85474313b23f48c2", "698be9ef85474313b23f48c5",
            "698be9ef85474313b23f48c8", "698be9ef85474313b23f48cb", "698be9ef85474313b23f48ce",
            "698be9ef85474313b23f48d1", "698be9ef85474313b23f48d4", "698be9ef85474313b23f48d7"
    );

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        seedTemplates();
        seedNotifications();
    }

    public void seedTemplates() {
        // --- IN_APP TEMPLATES (HTML formatted for App list) ---
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.IN_APP, "vi",
                "Lời mời kết bạn",
                "<b>{{actorName}}</b>{{#showSecondActor}} và <b>{{secondActorName}}</b>{{/showSecondActor}}{{#othersCount}} và <b>{{othersCount}} người khác</b>{{/othersCount}} đã gửi lời mời kết bạn cho bạn");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.IN_APP, "en",
                "Friend request",
                "<b>{{actorName}}</b>{{#showSecondActor}} and <b>{{secondActorName}}</b>{{/showSecondActor}}{{#othersCount}} and <b>{{othersCount}} others</b>{{/othersCount}} sent you a friend request");

        seedIfAbsent(NotificationType.MESSAGE_DIRECT, NotificationChannel.IN_APP, "vi",
                "Tin nhắn mới",
                "<b>{{actorName}}</b> đã gửi cho bạn một tin nhắn");
        seedIfAbsent(NotificationType.MESSAGE_DIRECT, NotificationChannel.IN_APP, "en",
                "New message",
                "<b>{{actorName}}</b> sent you a message");

        seedIfAbsent(NotificationType.POST_LIKE, NotificationChannel.IN_APP, "vi",
                "Lượt thích bài viết",
                "<b>{{actorName}}</b>{{#showSecondActor}} và <b>{{secondActorName}}</b>{{/showSecondActor}}{{#othersCount}} và <b>{{othersCount}} người khác</b>{{/othersCount}} đã thích bài viết của bạn");
        seedIfAbsent(NotificationType.POST_LIKE, NotificationChannel.IN_APP, "en",
                "Post liked",
                "<b>{{actorName}}</b>{{#showSecondActor}} and <b>{{secondActorName}}</b>{{/showSecondActor}}{{#othersCount}} and <b>{{othersCount}} others</b>{{/othersCount}} liked your post");

        seedIfAbsent(NotificationType.POST_COMMENT, NotificationChannel.IN_APP, "vi",
                "Bình luận mới",
                "<b>{{actorName}}</b> đã bình luận về bài viết của bạn");
        seedIfAbsent(NotificationType.POST_COMMENT, NotificationChannel.IN_APP, "en",
                "New comment",
                "<b>{{actorName}}</b> commented on your post");

        seedIfAbsent(NotificationType.POST_TAG, NotificationChannel.IN_APP, "vi",
                "Gắn thẻ",
                "<b>{{actorName}}</b> đã gắn thẻ bạn trong một bài viết");
        seedIfAbsent(NotificationType.POST_TAG, NotificationChannel.IN_APP, "en",
                "Tagged",
                "<b>{{actorName}}</b> tagged you in a post");

        seedIfAbsent(NotificationType.FRIEND_ACCEPT, NotificationChannel.IN_APP, "vi",
                "Chấp nhận kết bạn",
                "<b>{{actorName}}</b> đã chấp nhận lời mời kết bạn của bạn");
        seedIfAbsent(NotificationType.FRIEND_ACCEPT, NotificationChannel.IN_APP, "en",
                "Friend accepted",
                "<b>{{actorName}}</b> accepted your friend request");

        // --- FCM TEMPLATES (Standard Push Notifications) ---
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.FCM, "vi",
                "Lời mời kết bạn",
                "{{actorName}}{{#othersCount}} và {{othersCount}} người khác{{/othersCount}} đã gửi cho bạn lời mời kết bạn.");
        seedIfAbsent(NotificationType.FRIEND_REQUEST, NotificationChannel.FCM, "en",
                "New friend request",
                "{{actorName}}{{#othersCount}} and {{othersCount}} others{{/othersCount}} sent you a friend request.");

        seedIfAbsent(NotificationType.MESSAGE_DIRECT, NotificationChannel.FCM, "vi",
                "Tin nhắn mới",
                "{{actorName}} đã gửi cho bạn một tin nhắn.");
        seedIfAbsent(NotificationType.MESSAGE_DIRECT, NotificationChannel.FCM, "en",
                "New message",
                "{{actorName}} sent you a message.");

        seedIfAbsent(NotificationType.FRIEND_ACCEPT, NotificationChannel.FCM, "vi",
                "Chấp nhận kết bạn",
                "{{actorName}} đã chấp nhận lời mời kết bạn của bạn.");
        seedIfAbsent(NotificationType.FRIEND_ACCEPT, NotificationChannel.FCM, "en",
                "Friend accepted",
                "{{actorName}} accepted your friend request.");

        // --- SHARED SYSTEM TEMPLATES ---
        seedIfAbsent(NotificationType.SYSTEM, NotificationChannel.IN_APP, "vi",
                "Thông báo hệ thống",
                "{{message}}");
        seedIfAbsent(NotificationType.SYSTEM, NotificationChannel.IN_APP, "en",
                "System Notification",
                "{{message}}");

        seedIfAbsent(NotificationType.SYSTEM, NotificationChannel.FCM, "vi",
                "Thông báo mới",
                "{{message}}");
        seedIfAbsent(NotificationType.SYSTEM, NotificationChannel.FCM, "en",
                "New Notification",
                "{{message}}");

        seedIfAbsent(NotificationType.DLQ_ALERT, NotificationChannel.IN_APP, "vi",
                "Cảnh báo hệ thống",
                "Phát hiện <b>{{totalEventCount}}</b> lỗi xử lý mới tại topic <b>{{referenceId}}</b>. Tổng số lỗi chưa xử lý: <b>{{unresolvedCount}}</b>");
        seedIfAbsent(NotificationType.DLQ_ALERT, NotificationChannel.IN_APP, "en",
                "System Alert",
                "Detected <b>{{totalEventCount}}</b> new processing errors in topic <b>{{referenceId}}</b>. Total unresolved: <b>{{unresolvedCount}}</b>");

        seedIfAbsent(NotificationType.DLQ_ALERT, NotificationChannel.FCM, "vi",
                "Cảnh báo hệ thống",
                "Có {{totalEventCount}} lỗi mới tại {{referenceId}}. Tổng: {{unresolvedCount}}");
        seedIfAbsent(NotificationType.DLQ_ALERT, NotificationChannel.FCM, "en",
                "System Alert",
                "{{totalEventCount}} new errors in {{referenceId}}. Total: {{unresolvedCount}}");
    }

    private static final List<String> NAMES = List.of(
            "An", "Bình", "Cường", "Dũng", "Em", "Phượng", "Giang", "Hoa",
            "Hùng", "Hương", "Khoa", "Lan", "Minh", "Ngọc", "Nam", "Oanh",
            "Phú", "Quỳnh", "Sơn", "Tú", "Thảo", "Anh", "Đức", "Hà", "Linh"
    );

    private void seedNotifications() {
        Query query = new Query(Criteria.where("userId").is(new ObjectId(TARGET_USER_ID)));
        // Luôn xóa để đảm bảo data mock mới nhất có phân mốc thời gian đúng
        mongoTemplate.remove(query, "notifications");
        log.info("Cleared old notifications for user: {}", TARGET_USER_ID);

        LocalDateTime now = LocalDateTime.now();
        List<NotificationType> types = List.of(
                NotificationType.FRIEND_REQUEST, NotificationType.MESSAGE_DIRECT, NotificationType.POST_LIKE,
                NotificationType.POST_COMMENT, NotificationType.POST_TAG, NotificationType.FRIEND_ACCEPT,
                NotificationType.SYSTEM
        );

        // 1️⃣ BUCKET: NEWEST (Within 2 hours) -> 10 notifications
        for (int i = 0; i < 10; i++) {
            LocalDateTime time = now.minusMinutes(5 + i * 10);
            NotificationType type = types.get(i % types.size());
            seedNoti(type, time,
                    type == NotificationType.SYSTEM ? List.of() : List.of(ACTOR_IDS.get(i % ACTOR_IDS.size())),
                    i % 3 == 0,
                    NAMES.get(i % NAMES.size()));
        }

        // 2️⃣ BUCKET: TODAY (Older than 2 hours, includes 4-5 hours ago) -> 20 notifications
        for (int i = 0; i < 20; i++) {
            // This will range from roughly 2.5 hours ago to 7.5 hours ago
            LocalDateTime time = now.minusHours(2).minusMinutes(30 + i * 15);
            NotificationType type = types.get((i + 2) % types.size());
            seedNoti(type, time,
                    type == NotificationType.SYSTEM ? List.of() : List.of(ACTOR_IDS.get((i + 3) % ACTOR_IDS.size())),
                    i % 4 == 0,
                    NAMES.get((i + 10) % NAMES.size()));
        }

        // 3️⃣ BUCKET: PREVIOUS (Older than today) -> 70 notifications
        for (int i = 0; i < 70; i++) {
            LocalDateTime pastTime = now.minusDays(1 + (i / 10)).minusHours(i % 24).minusMinutes(i % 60);
            NotificationType type = types.get((i + 4) % types.size());
            seedNoti(type, pastTime,
                    type == NotificationType.SYSTEM ? List.of() : List.of(ACTOR_IDS.get((i + 5) % ACTOR_IDS.size())),
                    true,
                    NAMES.get((i + 5) % NAMES.size()));
        }

        log.info("Successfully seeded 100 mock notifications for user {}", TARGET_USER_ID);
    }

    private void seedNoti(NotificationType type, LocalDateTime time, List<String> actors, boolean isRead, String name) {
        // Chuyển LocalDateTime sang Date cho MongoDB Document
        Date date = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        
        Document doc = new Document();
        doc.append("userId", new ObjectId(TARGET_USER_ID));
        doc.append("type", type.name());
        doc.append("referenceId", new ObjectId()); // Add unique referenceId to avoid duplicate key error
        doc.append("actorIds", actors.stream().map(ObjectId::new).collect(Collectors.toList()));
        doc.append("isRead", isRead);
        
        // Prepare payload map for template rendering
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("actorCount", actors.size());
        payloadMap.put("othersCount", Math.max(0, actors.size() - 1));
        payloadMap.put("showSecondActor", actors.size() == 2);
        payloadMap.put("actorName", type == NotificationType.SYSTEM ? "Hệ thống" : name);
        payloadMap.put("actorAvatar", "https://i.pravatar.cc/150?u=" + name);
        
        if (actors.size() == 2) {
            String secondActorName = NAMES.get((NAMES.indexOf(name) + 1) % NAMES.size());
            payloadMap.put("secondActorName", secondActorName);
        }

        doc.append("payload", new Document(payloadMap));
        doc.append("createdAt", date);
        doc.append("lastModifiedAt", date);
        doc.append("_class", Notification.class.getName());

        mongoTemplate.getCollection("notifications").insertOne(doc);
    }

    private void seedIfAbsent(NotificationType type, NotificationChannel channel, String locale,
                               String titleTemplate, String bodyTemplate) {
        if (templateRepository.findByTypeAndChannelAndLocaleAndActiveTrue(type, channel, locale).isPresent()) return;
        templateRepository.save(NotificationTemplate.builder()
                .type(type)
                .channel(channel)
                .locale(locale)
                .titleTemplate(titleTemplate)
                .bodyTemplate(bodyTemplate)
                .build());
        log.info("Seeded template: type={}, channel={}, locale={}", type, channel, locale);
    }
}