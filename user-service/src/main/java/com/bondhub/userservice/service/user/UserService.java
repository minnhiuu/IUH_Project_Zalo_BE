package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse createUser(UserCreateRequest request);

    UserResponse getUserById(String id);

    UserResponse getUserByAccountId(String accountId);

    UserSummaryResponse getUserSummaryByAccountId(String accountId);

    UserResponse getMyUserWithAccountInfo();

    List<UserResponse> getAllUsers();

    UserResponse updateUser(String id, UserUpdateRequest request);

    void deleteUser(String id);
}
