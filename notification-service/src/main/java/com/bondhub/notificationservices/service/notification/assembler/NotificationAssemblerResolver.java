package com.bondhub.notificationservices.service.notification.assembler;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationAssemblerResolver {

    private final List<NotificationAssembler> assemblers;

    public NotificationAssembler get(NotificationType type) {
        return assemblers.stream()
                .filter(a -> a.getType() == type)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_STRATEGY_NOT_FOUND));
    }
}
