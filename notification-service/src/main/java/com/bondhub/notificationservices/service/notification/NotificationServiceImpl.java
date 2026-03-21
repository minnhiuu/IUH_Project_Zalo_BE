package com.bondhub.notificationservices.service.notification;

import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.notificationservices.client.UserServiceClient;
import com.bondhub.notificationservices.dto.response.notification.*;
import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.common.event.notification.RawNotificationEvent;
import com.bondhub.notificationservices.mapper.NotificationMapper;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserNotificationState;
import com.bondhub.notificationservices.publisher.RawNotificationPublisher;
import com.bondhub.notificationservices.repository.UserNotificationStateRepository;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationServiceImpl implements NotificationService {

    static final Duration FRESH_WINDOW = Duration.ofHours(2);

    RawNotificationPublisher rawPublisher;
    MongoTemplate mongoTemplate;
    NotificationMapper notificationMapper;
    SecurityUtil securityUtil;
    LocalizationUtil localizationUtil;
    NotificationTemplateService templateService;
    UserNotificationStateRepository userStateRepository;
    UserServiceClient userServiceClient;
    UserPreferenceService userPreferenceService;

    @Override
    public NotificationHistoryResponse getNotificationHistory(LocalDateTime cursor, int limit) {
        List<Notification> notifications = fetchNotifications(null, cursor, limit);
        return buildGroupedHistoryResponse(notifications, limit);
    }

    @Override
    public NotificationFlatHistoryResponse getUnreadHistory(LocalDateTime cursor, int limit) {
        List<Notification> notifications = fetchNotifications(true, cursor, limit);
        return buildUnreadHistoryResponse(notifications, limit);
    }

    private List<Notification> fetchNotifications(
            Boolean unreadOnly,
            LocalDateTime cursor,
            int limit) {
        String userId = securityUtil.getCurrentUserId();

        Criteria criteria = Criteria.where("userId").is(userId)
                .and("active").is(true);

        if (Boolean.TRUE.equals(unreadOnly)) {
            criteria.and("isRead").is(false);
        }

        if (cursor != null) {
            criteria.and("lastModifiedAt").lt(cursor);
        }

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "lastModifiedAt"))
                .limit(limit + 1);

        return mongoTemplate.find(query, Notification.class);
    }

    private NotificationHistoryResponse buildGroupedHistoryResponse(List<Notification> notifications, int limit) {
        if (notifications.isEmpty()) {
            return new NotificationHistoryResponse(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);
        }

        LocalDateTime nextCursor = null;
        List<Notification> itemsToProcess = notifications;
        if (notifications.size() > limit) {
            itemsToProcess = notifications.subList(0, limit);
            nextCursor = itemsToProcess.getLast().getLastModifiedAt();
        }

        String locale = localizationUtil.getCurrentLocale();
        List<NotificationType> types = itemsToProcess.stream().map(Notification::getType).distinct().toList();
        Map<NotificationType, NotificationTemplateResponse> templateMap = 
                templateService.getTemplates(types, NotificationChannel.IN_APP, locale);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime freshBoundary = now.minus(FRESH_WINDOW);
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();

        List<NotificationResponse> newest = new ArrayList<>();
        List<NotificationResponse> today = new ArrayList<>();
        List<NotificationResponse> previous = new ArrayList<>();

        for (Notification n : itemsToProcess) {
            NotificationResponse res = convertToResponse(n, locale, templateMap);
            LocalDateTime time = n.getLastModifiedAt();

            if (time.isAfter(freshBoundary)) {
                newest.add(res);
            } else if (time.isAfter(startOfToday)) {
                today.add(res);
            } else {
                previous.add(res);
            }
        }

        return new NotificationHistoryResponse(newest, today, previous, nextCursor);
    }

    private NotificationFlatHistoryResponse buildUnreadHistoryResponse(List<Notification> notifications, int limit) {
        if (notifications.isEmpty()) {
            return new NotificationFlatHistoryResponse(new ArrayList<>(), null);
        }

        LocalDateTime nextCursor = null;
        List<Notification> itemsToProcess = notifications;
        if (notifications.size() > limit) {
            itemsToProcess = notifications.subList(0, limit);
            nextCursor = itemsToProcess.getLast().getLastModifiedAt();
        }

        String locale = localizationUtil.getCurrentLocale();
        List<NotificationType> types = itemsToProcess.stream().map(Notification::getType).distinct().toList();
        Map<NotificationType, NotificationTemplateResponse> templateMap = 
                templateService.getTemplates(types, NotificationChannel.IN_APP, locale);

        List<NotificationResponse> items = itemsToProcess.stream()
                .map(n -> convertToResponse(n, locale, templateMap))
                .toList();

        return new NotificationFlatHistoryResponse(items, nextCursor);
    }

    @Override
    public UserNotificationStateResponse getNotificationState() {
        String userId = securityUtil.getCurrentUserId();
        UserNotificationState state = userStateRepository.findById(userId)
                .orElse(UserNotificationState.builder()
                        .userId(userId)
                        .unreadCount(0)
                        .build());

        return notificationMapper.toStateResponse(state);
    }

    @Override
    public void markHistoryAsChecked() {
        String userId = securityUtil.getCurrentUserId();
        mongoTemplate.upsert(
                new Query(Criteria.where("userId").is(userId)),
                new Update().set("lastCheckedAt", LocalDateTime.now()).set("unreadCount", 0L),
                UserNotificationState.class
        );
    }

    @Override
    public void markAsRead(String id) {
        String userId = securityUtil.getCurrentUserId();
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("_id").is(id)
                .and("isRead").is(false));

        Update update = new Update()
                .set("isRead", true)
                .set("readAt", LocalDateTime.now());

        mongoTemplate.findAndModify(query, update, Notification.class);
    }

    @Override
    public void markAllAsRead() {
        String userId = securityUtil.getCurrentUserId();
        Query query = new Query((Criteria) Criteria.where("userId").is(userId)
                .and("isRead").is(false));

        Update update = new Update()
                .set("isRead", true)
                .set("readAt", LocalDateTime.now());

        mongoTemplate.updateMulti(query, update, Notification.class);
    }

    @Override
    public void deactivateByReferenceIdAndType(String userId, String referenceId, NotificationType type) {
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("referenceId").is(referenceId)
                .and("type").is(type)
                .and("active").is(true));

        Update update = new Update().set("active", false);
        var result = mongoTemplate.updateMulti(query, update, Notification.class);
        log.info("[Notification] Deactivated {} notifications for referenceId={}, type={}, userId={}", 
                result.getModifiedCount(), referenceId, type, userId);
    }

    @Override
    public void sendTestNotification() {
        String userId = securityUtil.getCurrentUserId();
        log.info("[FCM] Triggering test notification for userId: {}", userId);

        var prefs = userPreferenceService.getPreferences(userId);
        String locale = prefs != null ? prefs.getLanguage() : "vi";
        String actorName = "BondHub Tester";
        String actorAvatar = "";

        try {
            var response = userServiceClient.getUserSummaryByUserId(userId);
            if (response.getBody() != null && response.getBody().data() != null) {
                actorName = response.getBody().data().fullName();
                actorAvatar = response.getBody().data().avatar();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user profile for test notification, using defaults");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorName", actorName);
        payload.put("actorAvatar", actorAvatar != null ? actorAvatar : "");
        payload.put("timestamp", LocalDateTime.now().toString());
        
        // Cung cấp các bản dịch khác nhau trong payload
        payload.put("message_en", "This is a test notification with AVATAR!");
        payload.put("message_vi", "Đây là thông báo test có AVATAR!");
        
        // Giữ lại field message mặc định để tương thích với các template cũ
        payload.put("message", "en".equalsIgnoreCase(locale) 
                ? "This is a test notification with AVATAR!" 
                : "Đây là thông báo test có AVATAR!");

        RawNotificationEvent event = RawNotificationEvent.builder()
                .recipientId(userId)
                .actorId(userId)
                .actorName(actorName)
                .actorAvatar(actorAvatar)
                .type(NotificationType.SYSTEM)
                .referenceId(userId)
                .payload(payload)
                .occurredAt(LocalDateTime.now())
                .build();

        rawPublisher.publish(event);
    }

    private NotificationResponse convertToResponse(
            Notification n, 
            String locale, 
            Map<NotificationType, NotificationTemplateResponse> templateMap) {
        
        NotificationTemplateResponse template = templateMap.get(n.getType());
        
        if (template == null) {
            log.warn("Template not found in batch for type={} locale={}", n.getType(), locale);
            return notificationMapper.toResponse(n, "", "");
        }

        String title = templateService.render(template.titleTemplate(), n.getPayload());
        String body = templateService.render(template.bodyTemplate(), n.getPayload());

        return notificationMapper.toResponse(n, title, body);
    }

}
