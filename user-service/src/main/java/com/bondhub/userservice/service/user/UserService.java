package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.UserProfileResponse;
import com.bondhub.userservice.dto.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserResponse createUser(UserCreateRequest request);

    UserResponse getUserById(String id);

    UserResponse getUserByAccountId(String accountId);

    UserSummaryResponse getUserSummaryByAccountId(String accountId);

    UserProfileResponse getMyUserWithAccountInfo();

    List<UserResponse> getAllUsers();

    UserProfileResponse updateUser(UserUpdateRequest request);

    String updateAvatar(MultipartFile file);

    String updateBackground(MultipartFile file);

    void deleteUser(String id);
}
