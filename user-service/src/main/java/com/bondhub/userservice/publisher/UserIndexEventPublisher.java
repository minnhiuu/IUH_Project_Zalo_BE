package com.bondhub.userservice.publisher;

import com.bondhub.common.event.user.UserIndexDeletedEvent;
import com.bondhub.common.event.user.UserIndexRequestedEvent;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import com.bondhub.userservice.dto.request.UserIndexRequest;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.repository.UserRepository;
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
    UserRepository userRepository;

    @Transactional
    public void publishIndexRequest(UserIndexRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserIndexRequestedEvent event = UserIndexRequestedEvent.builder()
                .userId(request.userId())
                .fullName(user.getFullName())
                .avatar(user.getAvatar())
                .accountId(user.getAccountId())
                .phoneNumber(request.phoneNumber())
                .role(request.role())
                .timestamp(System.currentTimeMillis())
                .build();

        outboxEventPublisher.saveAndPublish(user.getId(), "User", EventType.USER_INDEX_REQUESTED, event);
        log.info("Published USER_INDEX_REQUESTED: userId={}", user.getId());
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

    @Transactional
    public void publishIndexRequestBatch(UserIndexRequest request) {
        publishIndexRequest(request);
    }
}
