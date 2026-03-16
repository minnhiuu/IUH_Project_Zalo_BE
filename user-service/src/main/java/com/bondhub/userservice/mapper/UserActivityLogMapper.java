package com.bondhub.userservice.mapper;

import com.bondhub.userservice.dto.response.UserActivityLogResponse;
import com.bondhub.userservice.model.UserActivityLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserActivityLogMapper {
    
    UserActivityLogResponse toUserActivityLogResponse(UserActivityLog userActivityLog);
}
