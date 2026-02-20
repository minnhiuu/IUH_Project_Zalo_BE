package com.bondhub.notificationservices.strategy.content.factory;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.strategy.content.NotificationContentStrategy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ContentStrategyFactory {

    List<NotificationContentStrategy> strategies;

    public NotificationContentStrategy get(NotificationType type) {

        return strategies.stream()
                .filter(s -> s.getType() == type)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_STRATEGY_NOT_FOUND));
    }
}
