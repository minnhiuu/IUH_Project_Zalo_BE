package com.bondhub.userservice.mapper;

import com.bondhub.userservice.dto.response.user.AccountResponse;
import com.bondhub.userservice.dto.response.AuditResponse;
import com.bondhub.userservice.dto.response.UserAdminDetailResponse;
import com.bondhub.userservice.dto.response.UserAdminResponse;
import com.bondhub.userservice.dto.response.user.UserResponse;
import com.bondhub.userservice.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminUserMapper {

    AuditResponse toAuditResponse(User user);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "dob", source = "user.dob")
    @Mapping(target = "bio", source = "user.bio")
    @Mapping(target = "gender", source = "user.gender")
    @Mapping(target = "avatar", expression = "java(user.getAvatar() != null ? baseUrl + user.getAvatar() : null)")
    @Mapping(target = "background", expression = "java(user.getBackground() != null ? baseUrl + user.getBackground() : null)")
    @Mapping(target = "backgroundY", source = "user.backgroundY")
    @Mapping(target = "accountInfo", source = "account")
    UserResponse toUserResponse(User user, AccountResponse account, String baseUrl);

    default UserAdminResponse toUserAdminResponse(User user, AccountResponse account, String baseUrl) {
        return UserAdminResponse.builder()
                .user(toUserResponse(user, account, baseUrl))
                .audit(toAuditResponse(user))
                .build();
    }

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "dob", source = "user.dob")
    @Mapping(target = "bio", source = "user.bio")
    @Mapping(target = "gender", source = "user.gender")
    @Mapping(target = "avatar", expression = "java(user.getAvatar() != null ? baseUrl + user.getAvatar() : null)")
    @Mapping(target = "background", expression = "java(user.getBackground() != null ? baseUrl + user.getBackground() : null)")
    @Mapping(target = "backgroundY", source = "user.backgroundY")
    @Mapping(target = "accountId", source = "user.accountId")
    @Mapping(target = "email", expression = "java(account != null ? account.email() : null)")
    @Mapping(target = "phoneNumber", expression = "java(account != null ? account.phoneNumber() : null)")
    @Mapping(target = "role", expression = "java(account != null ? account.role() : null)")
    @Mapping(target = "active", source = "user.active")
    @Mapping(target = "createdAt", source = "user.createdAt")
    @Mapping(target = "lastModifiedAt", source = "user.lastModifiedAt")
    @Mapping(target = "createdBy", source = "user.createdBy")
    @Mapping(target = "lastModifiedBy", source = "user.lastModifiedBy")
    @Mapping(target = "lastLoginAt", source = "user.lastLoginAt")
    @Mapping(target = "totalActivityLogs", source = "totalActivityLogs")
    @Mapping(target = "banReason", source = "banReason")
    UserAdminDetailResponse toUserAdminDetailResponse(User user, AccountResponse account, String baseUrl, long totalActivityLogs, String banReason);
}
