package com.bondhub.common.scheduler;

import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.common.model.kafka.OutboxEvent;
import com.bondhub.common.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventRetryScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    private static final int MAX_RETRIES = 5;

    /**
     * Retry failed events every 5 minutes
     */
    @Scheduled(fixedDelayString = "${outbox.retry.interval:300000}") // 5 minutes default
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository.findFailedEventsForRetry(MAX_RETRIES);

        if (failedEvents.isEmpty()) {
            return;
        }

        log.info("🔄 Found {} failed events to retry", failedEvents.size());

        for (OutboxEvent event : failedEvents) {
            try {
                log.info("🔄 Retrying failed event: eventId={}, retryCount={}, eventType={}",
                        event.getId(), event.getRetryCount(), event.getEventType());

                outboxEventPublisher.publishToKafka(event);

            } catch (Exception e) {
                log.error("❌ Retry failed for event: eventId={}, error={}",
                        event.getId(), e.getMessage());
            }
        }
    }

    /**
     * Move events that exceeded max retries to dead letter status
     */
    @Scheduled(fixedDelayString = "${outbox.dead-letter.check-interval:600000}") // 10 minutes default
    public void handleDeadLetterEvents() {
        List<OutboxEvent> deadLetterEvents = outboxEventRepository
                .findByStatusAndRetryCountGreaterThanEqual(
                        OutboxEvent.OutboxEventStatus.FAILED,
                        MAX_RETRIES
                );

        if (deadLetterEvents.isEmpty()) {
            return;
        }

        log.warn("⚠️ Found {} events that exceeded max retries (dead letter candidates)",
                deadLetterEvents.size());

        for (OutboxEvent event : deadLetterEvents) {
            log.error("💀 Dead letter event: eventId={}, eventType={}, aggregateId={}, retryCount={}, error={}",
                    event.getId(),
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getRetryCount(),
                    event.getErrorMessage());

            // TODO: Send notification to monitoring system
            // TODO: Optionally move to dead letter collection
        }
    }
}
