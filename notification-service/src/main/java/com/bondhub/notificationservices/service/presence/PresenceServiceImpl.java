package com.bondhub.notificationservices.service.presence;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PresenceServiceImpl implements PresenceService {

    static final String PRESENCE_KEY_PREFIX = "user:online:";

    StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean isOnline(String userId) {
        try {
            Boolean exists = stringRedisTemplate.hasKey(PRESENCE_KEY_PREFIX + userId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Presence check failed for userId={}, defaulting to offline: {}", userId, e.getMessage());
            return false;
        }
    }
}
