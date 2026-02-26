package com.bondhub.common.scheduler;

import com.bondhub.common.model.kafka.OutboxEvent;
import com.bondhub.common.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Auto cleanup old PUBLISHED events from Outbox
 * Retention: 7 days (configurable)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void cleanupPublishedEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        long deleted = outboxEventRepository.deleteByStatusAndCreatedAtBefore(
                OutboxEvent.OutboxEventStatus.PUBLISHED,
                cutoff
        );

        log.info("🧹 Cleaned up {} PUBLISHED outbox events older than 7 days", deleted);
    }

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    public void alertStuckEvents() {
        LocalDateTime stuckThreshold = LocalDateTime.now().minusHours(1);

        long stuckCount = outboxEventRepository.countByStatusAndCreatedAtBefore(
                OutboxEvent.OutboxEventStatus.PENDING,
                stuckThreshold
        );

        if (stuckCount > 0) {
            log.warn("⚠️ Found {} stuck PENDING events (> 1 hour old)", stuckCount);
            // TODO: Send alert to Slack/PagerDuty
        }
    }
}