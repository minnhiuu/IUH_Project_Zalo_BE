package com.bondhub.notificationservices.service.frequency;

import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FrequencyControlServiceImpl implements FrequencyControlService {

    private final NotificationRepository repository;

    private static final int MAX_PER_MINUTE = 5;

    @Override
    public boolean allow(String userId, NotificationType type) {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);

        long count = repository.countByUserIdAndTypeAndCreatedAtAfter(
                        userId,
                        type,
                        oneMinuteAgo
                );

        return count < MAX_PER_MINUTE;
    }
}
