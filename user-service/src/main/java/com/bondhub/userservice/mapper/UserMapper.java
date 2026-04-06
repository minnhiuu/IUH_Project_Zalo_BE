package com.bondhub.userservice.mapper;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.request.user.UserCreateRequest;
import com.bondhub.common.dto.client.userservice.user.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.user.UserResponse;
import com.bondhub.userservice.dto.response.user.UserImageResponse;
import com.bondhub.userservice.model.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {
    User toUser(UserCreateRequest request);

    UserResponse toUserResponse(User user);

    UserSummaryResponse toUserSummaryResponse(User user);

//    UserSummaryResponse toUserSummaryResponse(UserIndex userIndex);

    @Mapping(target = "url", expression = "java(baseUrl + user.getAvatar())")
    @Mapping(target = "y", ignore = true)
    UserImageResponse toAvatarResponse(User user, String baseUrl);

    @Mapping(target = "url", expression = "java(baseUrl + user.getBackground())")
    @Mapping(target = "y", source = "user.backgroundY")
    UserImageResponse toBackgroundResponse(User user, String baseUrl);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromRequest(@MappingTarget User user, UserUpdateRequest request);
}
