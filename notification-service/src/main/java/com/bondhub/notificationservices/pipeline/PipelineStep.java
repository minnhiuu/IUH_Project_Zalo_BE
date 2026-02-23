package com.bondhub.notificationservices.pipeline;

import com.bondhub.notificationservices.event.RawNotificationEvent;

public interface PipelineStep {
    boolean process(RawNotificationEvent event);
}
