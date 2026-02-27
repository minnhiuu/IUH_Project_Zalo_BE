package com.bondhub.notificationservices.mapper;

import com.bondhub.notificationservices.dto.response.notification.NotificationGroupResponse;
import com.bondhub.notificationservices.model.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "actorCount", expression = "java(notification.getActorIds() != null ? notification.getActorIds().size() : 0)")
    NotificationGroupResponse toGroupResponse(Notification notification);
}
