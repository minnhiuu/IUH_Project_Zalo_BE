package com.bondhub.notificationservices.mapper;

import com.bondhub.notificationservices.dto.response.NotificationResponse;
import com.bondhub.notificationservices.model.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
