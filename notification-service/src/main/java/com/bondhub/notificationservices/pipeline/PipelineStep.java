package com.bondhub.notificationservices.pipeline;

import com.bondhub.common.event.notification.RawNotificationEvent;

public interface PipelineStep {
    boolean process(RawNotificationEvent event);
}
