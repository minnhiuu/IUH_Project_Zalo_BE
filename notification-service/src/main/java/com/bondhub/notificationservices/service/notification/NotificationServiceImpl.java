package com.bondhub.notificationservices.service.notification;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.notificationservices.dto.request.notification.CreateFriendRequestNotificationRequest;
import com.bondhub.notificationservices.dto.response.notification.NotificationAcceptedResponse;
import com.bondhub.notificationservices.dto.response.notification.NotificationGroupResponse;
import com.bondhub.notificationservices.enums.NotificationType;
import com.bondhub.notificationservices.event.RawNotificationEvent;
import com.bondhub.notificationservices.mapper.NotificationMapper;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.publisher.RawNotificationPublisher;
import com.bondhub.notificationservices.repository.NotificationRepository;
import com.bondhub.notificationservices.service.notification.assembler.NotificationAssemblerResolver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationServiceImpl implements NotificationService {

    NotificationAssemblerResolver assemblerResolver;
    RawNotificationPublisher rawPublisher;
    NotificationRepository notificationRepository;
    MongoTemplate mongoTemplate;
    NotificationMapper notificationMapper;
    SecurityUtil securityUtil;

    @Override
    public NotificationAcceptedResponse createFriendRequestNotification(
            CreateFriendRequestNotificationRequest request) {
        return enqueue(NotificationType.FRIEND_REQUEST, request);
    }

    @Override
    public PageResponse<List<NotificationGroupResponse>> getMyNotifications(Pageable pageable) {
        String userId = securityUtil.getCurrentUserId();
        Criteria criteria = Criteria.where("userId").is(userId);

        long total = mongoTemplate.count(new Query(criteria), Notification.class);
        List<Notification> notifications = mongoTemplate.find(new Query(criteria).with(pageable), Notification.class);

        Page<Notification> raw = new PageImpl<>(notifications, pageable, total);
        return PageResponse.fromPage(raw, notificationMapper::toGroupResponse);
    }

    private NotificationAcceptedResponse enqueue(NotificationType type, Object request) {
        RawNotificationEvent event = assemblerResolver.get(type).build(request);
        log.info("[API] Enqueueing notification: type={}, recipient={}", type, event.getRecipientId());
        rawPublisher.publish(event);
        return NotificationAcceptedResponse.queued();
    }
}
