package com.bondhub.notificationservices.service.delivery.handler;

import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.repository.NotificationRepository;
import com.bondhub.notificationservices.service.notificationtemplate.NotificationTemplateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InAppDeliveryHandler implements DeliveryHandler {

    NotificationRepository notificationRepository;
    NotificationTemplateService templateService;
    MongoTemplate mongoTemplate;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(BatchedNotificationEvent event) {
        List<String> actorIds = event.getActorIds() != null ? event.getActorIds() : List.of();

        Query query = new Query(Criteria.where("userId").is(event.getRecipientId())
                .and("type").is(event.getType())
                .and("referenceId").is(null));

        Update update = new Update()
                .push("actorIds").atPosition(Update.Position.FIRST).each(actorIds.toArray())
                .set("isRead", false)
                .set("lastModifiedAt", java.time.LocalDateTime.now());

        Notification existing = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Notification.class
        );

        if (existing != null) {
            Set<String> uniqueActors = new LinkedHashSet<>(existing.getActorIds());
            List<String> finalActors = new ArrayList<>(uniqueActors);
            existing.setActorIds(finalActors);

            Map<String, Object> dynamicData = new HashMap<>();
            dynamicData.put("firstName", event.getFirstActorName() != null ? event.getFirstActorName() : event.getFirstActorId());
            dynamicData.put("count", finalActors.size());
            dynamicData.put("othersCount", finalActors.size() - 1);

            existing.setTitle(templateService.renderTitle(event.getType(), NotificationChannel.IN_APP, event.getLocale(), dynamicData));
            existing.setBody(templateService.renderBody(event.getType(), NotificationChannel.IN_APP, event.getLocale(), dynamicData));
            existing.setData(buildDataFromCount(event, finalActors.size()));

            notificationRepository.save(existing);
            log.info("IN_APP atomic delivery: recipientId={}, totalActors={}",
                    event.getRecipientId(), finalActors.size());
        }

        // TODO: push realtime to client via WebSocket (if online)
    }

    private Map<String, Object> buildTemplateData(BatchedNotificationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName",   event.getFirstActorName() != null ? event.getFirstActorName() : event.getFirstActorId());
        data.put("othersCount", event.getOthersCount());
        data.put("count",       event.getActorCount());
        return data;
    }

    private Map<String, Object> buildData(BatchedNotificationEvent event) {
        return buildDataFromCount(event, event.getActorCount());
    }

    private Map<String, Object> buildDataFromCount(BatchedNotificationEvent event, int totalCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("actorId",     event.getFirstActorId());
        data.put("actorName",   event.getFirstActorName());
        data.put("actorAvatar", event.getFirstActorAvatar());
        data.put("actorCount",  totalCount);
        data.put("othersCount", totalCount - 1);
        return data;
    }

    private String renderSafe(BatchedNotificationEvent event, Map<String, Object> data, String field) {
        try {
            return "title".equals(field)
                    ? templateService.renderTitle(event.getType(), NotificationChannel.IN_APP, event.getLocale(), data)
                    : templateService.renderBody(event.getType(), NotificationChannel.IN_APP, event.getLocale(), data);
        } catch (Exception e) {
            log.warn("IN_APP template not found for type={} locale={}, using fallback", event.getType(), event.getLocale());
            return "title".equals(field) ? event.getActorCount() + " new notifications" : "";
        }
    }
}