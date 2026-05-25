package com.bondhub.notificationservices.task;

import com.bondhub.notificationservices.model.Notification;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationCleanupTask {

    MongoTemplate mongoTemplate;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupInactiveNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        
        Query query = new Query(Criteria.where("active").is(false)
                .and("lastModifiedAt").lt(cutoff));
        
        var result = mongoTemplate.remove(query, Notification.class);
        
        if (result.getDeletedCount() > 0) {
            log.info("[CleanupTask] Purged {} inactive notifications older than 7 days", 
                    result.getDeletedCount());
        }
    }
}
