package com.bondhub.userservice.mapper;

import com.bondhub.userservice.dto.response.user.AccountResponse;
import com.bondhub.userservice.dto.response.user.UserProfileResponse;
import com.bondhub.userservice.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "phoneNumber", source = "account.phoneNumber")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "role", source = "account.role")
    UserProfileResponse toUserProfileResponse(User user, AccountResponse account);
}
