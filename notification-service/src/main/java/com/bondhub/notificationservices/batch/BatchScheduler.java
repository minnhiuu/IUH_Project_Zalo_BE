package com.bondhub.notificationservices.batch;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BatchScheduler {

    TaskScheduler taskScheduler;
    BatchFlushService batchFlushService;

    public void scheduleFlush(String batchKey, int windowSeconds) {
        Instant executeAt = Instant.now().plusSeconds(windowSeconds);
        log.debug("Scheduling flush batchKey={} at {}", batchKey, executeAt);

        taskScheduler.schedule(
                () -> {
                    try {
                        batchFlushService.flush(batchKey);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }, executeAt
        );
    }
}
