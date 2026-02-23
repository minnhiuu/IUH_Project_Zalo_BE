package com.bondhub.notificationservices.pipeline;

import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.bondhub.notificationservices.batch.BatcherService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationBatcherStep implements PipelineStep {

    BatcherService batcherService;

    @Override
    public boolean process(RawNotificationEvent event) {
        boolean buffered = batcherService.buffer(event);
        return !buffered;
    }
}
