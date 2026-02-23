package com.bondhub.notificationservices.pipeline;

import com.bondhub.notificationservices.event.RawNotificationEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserPreferenceCheckerStep implements PipelineStep {

    // TODO: Call Feign Client to user-service to get user notification setting
    @Override
    public boolean process(RawNotificationEvent event) {
        return true;
    }
}
