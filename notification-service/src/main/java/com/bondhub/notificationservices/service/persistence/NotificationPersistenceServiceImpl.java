package com.bondhub.notificationservices.service.persistence;

import com.bondhub.common.enums.NotificationType;
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

    private static final int MAX_CHAT_SNIPPET_WORDS = 80;

    NotificationRepository notificationRepository;
    MongoTemplate mongoTemplate;
    MessageSource messageSource;

    @Override
    public Notification persist(BatchedNotificationEvent event) {
        if (isChatMessageType(event.getType())) {
            String conversationId = resolveConversationId(event);
            return conversationId != null
                    ? persistPerEntity(event, conversationId)
                    : persistAggregate(event);
        }

        BatchWindowConfig cfg = BatchWindowConfig.of(event.getType());

        if (!cfg.isIncludeReferenceInKey()) {
            return persistAggregate(event);
        }

        return event.getReferenceId() != null
                ? persistPerEntity(event)
                : persistAggregate(event);
    }

    private Notification persistPerEntity(BatchedNotificationEvent event) {
        return persistPerEntity(event, event.getReferenceId());
    }

    private Notification persistPerEntity(BatchedNotificationEvent event, String referenceId) {
        List<String> rawActorIds = event.getActorIds() != null ? event.getActorIds() : List.of();
        List<ObjectId> actorObjectIds = rawActorIds.stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .toList();

        Query query = new Query(Criteria.where("userId").is(event.getRecipientId())
                .and("type").is(event.getType())
                .and("referenceId").is(referenceId));

        Update update = new Update()
                .push("actorIds").atPosition(Update.Position.LAST).each(actorObjectIds.toArray())
                .set("isRead", false)
                .set("lastModifiedAt", event.getLastOccurredAt());

        Notification old = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(false).upsert(true),
                Notification.class
        );

        boolean shouldIncrement = (old == null || old.isRead());
        Notification persisted = mongoTemplate.findOne(query, Notification.class);

        if (persisted == null) return null;

        return finalizeAndSave(persisted, event, shouldIncrement);
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

        Notification old = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(false).upsert(true),
                Notification.class
        );

        boolean shouldIncrement = (old == null || old.isRead());
        Notification persisted = mongoTemplate.findOne(query, Notification.class);

        if (persisted == null) return null;

        return finalizeAndSave(persisted, event, shouldIncrement);
    }

    private Notification finalizeAndSave(Notification persisted, BatchedNotificationEvent event, boolean shouldIncrement) {
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

        // Aggregation logic for Chat messages
        if (isChatMessageType(event.getType())) {
            // If it's a "new" notification (previous was read or null), start a fresh snippets list
            List<String> snippets;
            if (shouldIncrement) {
                snippets = new ArrayList<>();
            } else {
                snippets = (List<String>) persisted.getPayload().get("snippets");
                if (snippets == null) snippets = new ArrayList<>();
            }
            
            String actorName = normalizeSnippet(event.getLastActorName());
            String content = normalizeSnippet((String) basePayload.get("content"));
            if (content != null) {
                String newSnippet = (event.getType() == NotificationType.MESSAGE_GROUP && actorName != null) 
                    ? actorName + ": " + content 
                    : content;
                
                // Add if not duplicate of the last one
                if (snippets.isEmpty() || !snippets.get(snippets.size() - 1).equals(newSnippet)) {
                    snippets.add(newSnippet);
                }
                
                payloadMap.put("snippets", limitByWordCount(snippets, MAX_CHAT_SNIPPET_WORDS));
            }
        }

        persisted.setPayload(payloadMap);
        Notification saved = notificationRepository.save(persisted);

        if (shouldIncrement) {
            if (!isChatMessageType(event.getType())) {
                updateUnreadState(event.getRecipientId(), event.getLastActorId(), event.getType(), event.getReferenceId());
                incrementRawUnreadCount(event.getRecipientId());
            }
        }
        return saved;
    }

    @Override
    public Notification buildTransient(BatchedNotificationEvent event) {
        List<Map<String, Object>> payloads = event.getRawPayloads() != null ? event.getRawPayloads() : List.of();
        Map<String, Object> basePayload = !payloads.isEmpty() ? payloads.get(payloads.size() - 1) : Map.of();
        Map<String, Object> payloadMap = new HashMap<>(basePayload);

        String convAvatar = (String) basePayload.get("conversationAvatar");
        payloadMap.put("actorName", event.getLastActorName());
        payloadMap.put("actorAvatar", (convAvatar != null && !convAvatar.isEmpty()) ? convAvatar : event.getLastActorAvatar());
        payloadMap.put("actorCount", 1);
        payloadMap.put("othersCount", 0);
        payloadMap.put("totalEventCount", 1);
        payloadMap.put("showSecondActor", false);

        return Notification.builder()
                .id(UUID.randomUUID().toString())
                .userId(event.getRecipientId())
                .type(event.getType())
                .referenceId(event.getReferenceId())
                .actorIds(List.of(event.getLastActorId()))
                .payload(payloadMap)
                .isRead(false)
                .lastModifiedAt(event.getLastOccurredAt())
                .build();
    }

    private String getTranslatedFallback(String localeStr) {
        Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : new Locale("vi");
        return messageSource.getMessage("notification.another_person", null, locale);
    }

    private List<String> limitByWordCount(List<String> snippets, int maxWords) {
        LinkedList<String> result = new LinkedList<>();
        int totalWords = 0;

        ListIterator<String> iterator = snippets.listIterator(snippets.size());
        while (iterator.hasPrevious()) {
            String snippet = normalizeSnippet(iterator.previous());
            if (snippet == null) continue;
            int wordCount = countWords(snippet);

            if (wordCount > maxWords) {
                snippet = truncateWords(snippet, maxWords);
                wordCount = countWords(snippet);
            }

            if (!result.isEmpty() && totalWords + wordCount > maxWords) {
                break;
            }

            result.addFirst(snippet);
            totalWords += wordCount;
        }

        return result;
    }

    private String normalizeSnippet(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int countWords(String value) {
        if (value == null || value.isBlank()) return 0;
        return value.trim().split("\\s+").length;
    }

    private String truncateWords(String value, int maxWords) {
        if (value == null || value.isBlank()) return "";
        String[] words = value.trim().split("\\s+");
        if (words.length <= maxWords) return value;

        return String.join(" ", Arrays.copyOf(words, maxWords)) + "...";
    }

    private boolean isChatMessageType(NotificationType type) {
        return type == NotificationType.MESSAGE_DIRECT || type == NotificationType.MESSAGE_GROUP;
    }

    private String resolveConversationId(BatchedNotificationEvent event) {
        List<Map<String, Object>> payloads = event.getRawPayloads() != null ? event.getRawPayloads() : List.of();
        if (!payloads.isEmpty()) {
            Object conversationId = payloads.get(payloads.size() - 1).get("conversationId");
            if (conversationId != null && !conversationId.toString().isBlank()) {
                return conversationId.toString();
            }
        }

        return event.getReferenceId();
    }

    @Override
    public void updateUnreadState(String userId, String actorId, com.bondhub.common.enums.NotificationType type, String referenceId) {
        if (actorId == null || type == null) return;
        
        String compositeKey = actorId + "_" + type.name();
        if (referenceId != null) {
            compositeKey += "_" + referenceId;
        }

        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .addToSet("unreadActorIds", compositeKey);
        
        mongoTemplate.upsert(query, update, UserNotificationState.class);
    }

    private void incrementRawUnreadCount(String userId) {
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().inc("unreadCount", 1L),
                UserNotificationState.class
        );
    }
}
