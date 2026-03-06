package com.bondhub.notificationservices.mapper;

import com.bondhub.notificationservices.dto.response.notification.NotificationResponse;
import com.bondhub.notificationservices.dto.response.notification.UserNotificationStateResponse;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.model.UserNotificationState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "actorCount", expression = "java(notification.getActorIds() != null ? notification.getActorIds().size() : 0)")
    @Mapping(target = "read", source = "notification.read")
    @Mapping(target = "payload", source = "notification.payload")
    NotificationResponse toResponse(Notification notification, String title, String body);

    UserNotificationStateResponse toStateResponse(UserNotificationState state);
}
