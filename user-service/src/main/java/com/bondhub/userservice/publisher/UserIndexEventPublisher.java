package com.bondhub.userservice.publisher;

import com.bondhub.common.event.user.UserIndexDeletedEvent;
import com.bondhub.common.event.user.UserIndexRequestedEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.userservice.dto.request.elasticsearch.UserIndexRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserIndexEventPublisher {

    OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publishIndexRequest(UserIndexRequest request) {
        UserIndexRequestedEvent event = UserIndexRequestedEvent.builder()
                .userId(request.userId())
                .fullName(request.fullName())
                .avatar(request.avatar())
                .accountId(request.accountId())
                .phoneNumber(request.phoneNumber())
                .role(request.role())
                .timestamp(System.currentTimeMillis())
                .build();

        outboxEventPublisher.saveAndPublish(request.userId(), "User", EventType.USER_INDEX_REQUESTED, event);
        log.info("Published USER_INDEX_REQUESTED: userId={}", request.userId());
    }

    @Transactional
    public void publishDeleteRequest(String userId) {
        UserIndexDeletedEvent event = UserIndexDeletedEvent.builder()
                .userId(userId)
                .timestamp(System.currentTimeMillis())
                .build();

        outboxEventPublisher.saveAndPublish(
                userId,
                "User",
                EventType.USER_INDEX_DELETED,
                event
        );

        log.info("Published USER_INDEX_DELETED: userId={}", userId);
    }
}
