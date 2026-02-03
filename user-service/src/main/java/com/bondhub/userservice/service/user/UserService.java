package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.request.AvatarUpdateRequest;
import com.bondhub.userservice.dto.request.BackgroundUpdateRequest;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.UserImageResponse;
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

    UserImageResponse updateAvatar(AvatarUpdateRequest request);

    UserImageResponse updateBackground(BackgroundUpdateRequest request);

    UserImageResponse updateBackgroundPosition(Double y);

    void deleteUser(String id);
}
