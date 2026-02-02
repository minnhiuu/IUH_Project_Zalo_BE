package com.bondhub.userservice.mapper;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.dto.response.UserImageResponse;
import com.bondhub.userservice.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserCreateRequest request);

    UserResponse toUserResponse(User user);

    UserSummaryResponse toUserSummaryResponse(User user);

    @Mapping(target = "url", expression = "java(baseUrl + user.getAvatar())")
    @Mapping(target = "y", ignore = true)
    UserImageResponse toAvatarResponse(User user, String baseUrl);

    @Mapping(target = "url", expression = "java(baseUrl + user.getBackground())")
    @Mapping(target = "y", source = "user.backgroundY")
    UserImageResponse toBackgroundResponse(User user, String baseUrl);

    void updateUserFromRequest(@MappingTarget User user, UserUpdateRequest request);
}
