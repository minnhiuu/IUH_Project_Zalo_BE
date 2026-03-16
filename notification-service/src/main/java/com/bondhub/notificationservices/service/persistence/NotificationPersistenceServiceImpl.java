package com.bondhub.notificationservices.service.persistence;

import com.bondhub.notificationservices.enums.BatchWindowConfig;
import com.bondhub.notificationservices.event.BatchedNotificationEvent;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserNotificationState;
import com.bondhub.notificationservices.repository.NotificationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.context.MessageSource;
import java.util.Locale;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationPersistenceServiceImpl implements NotificationPersistenceService {

    NotificationRepository notificationRepository;
    MongoTemplate mongoTemplate;
    MessageSource messageSource;

    @Override
    public Notification persist(BatchedNotificationEvent event) {
        BatchWindowConfig cfg = BatchWindowConfig.of(event.getType());

        if (!cfg.isIncludeReferenceInKey()) {
            return persistAggregate(event);
        }

        return event.getReferenceId() != null
                ? persistPerEntity(event)
                : persistAggregate(event);
    }

    private Notification persistPerEntity(BatchedNotificationEvent event) {
        List<String> rawActorIds = event.getActorIds() != null ? event.getActorIds() : List.of();
        List<ObjectId> actorObjectIds = rawActorIds.stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .toList();

        Query query = new Query(Criteria.where("userId").is(event.getRecipientId())
                .and("type").is(event.getType())
                .and("referenceId").is(event.getReferenceId()));

        Update update = new Update()
                .push("actorIds").atPosition(Update.Position.LAST).each(actorObjectIds.toArray())
                .set("isRead", false)
                .set("lastModifiedAt", event.getLastOccurredAt());

        Notification persisted = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Notification.class
        );

        if (persisted == null) return null;

        return finalizeAndSave(persisted, event);
    }

    private Notification persistAggregate(BatchedNotificationEvent event) {
        List<String> rawActorIds = event.getActorIds() != null ? event.getActorIds() : List.of();
        List<ObjectId> actorObjectIds = rawActorIds.stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .toList();

        Query query = new Query(Criteria.where("userId").is(event.getRecipientId())
                .and("type").is(event.getType())
                .and("referenceId").is(null));

        Update update = new Update()
                .push("actorIds").atPosition(Update.Position.LAST).each(actorObjectIds.toArray())
                .set("isRead", false)
                .set("lastModifiedAt", event.getLastOccurredAt());

        Notification persisted = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Notification.class
        );

        if (persisted == null) return null;

        return finalizeAndSave(persisted, event);
    }

    private Notification finalizeAndSave(Notification persisted, BatchedNotificationEvent event) {
        Set<String> unique = new LinkedHashSet<>(persisted.getActorIds());
        List<String> finalActors = new ArrayList<>(unique);
        persisted.setActorIds(finalActors);

        int actorCount = finalActors.size();
        int othersCount = Math.max(0, actorCount - 1);

        List<Map<String, Object>> payloads = event.getRawPayloads() != null ? event.getRawPayloads() : List.of();
        Map<String, Object> basePayload = !payloads.isEmpty() ? payloads.get(payloads.size() - 1) : Map.of();
        Map<String, Object> payloadMap = new HashMap<>(basePayload);

        payloadMap.put("actorName", event.getLastActorName());
        payloadMap.put("actorAvatar", event.getLastActorAvatar());
        payloadMap.put("actorCount", actorCount);
        payloadMap.put("othersCount", othersCount);
        payloadMap.put("totalEventCount", event.getTotalEventCount());
        payloadMap.put("showSecondActor", actorCount == 2);

        if (actorCount == 2) {
            try {
                String secondToLastId = finalActors.get(finalActors.size() - 2);
                String secondName = payloads.stream()
                        .filter(p -> secondToLastId.equals(p.get("actorId")))
                        .map(p -> (String) p.get("actorName"))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseGet(() -> getTranslatedFallback(event.getLocale()));
                payloadMap.put("secondActorName", secondName);
            } catch (Exception e) {
                payloadMap.put("secondActorName", getTranslatedFallback(event.getLocale()));
            }
        }

        persisted.setPayload(payloadMap);
        Notification saved = notificationRepository.save(persisted);

        incrementUnreadCount(event.getRecipientId());
        return saved;
    }

    private String getTranslatedFallback(String localeStr) {
        Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : new Locale("vi");
        return messageSource.getMessage("notification.another_person", null, locale);
    }

    private void incrementUnreadCount(String userId) {
        mongoTemplate.upsert(
                new Query(Criteria.where("userId").is(userId)),
                new Update().inc("unreadCount", 1L),
                UserNotificationState.class
        );
    }
}
