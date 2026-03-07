package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.userservice.dto.request.user.AvatarUpdateRequest;
import com.bondhub.userservice.dto.request.user.BackgroundUpdateRequest;
import com.bondhub.userservice.dto.request.BioUpdateRequest;
import com.bondhub.userservice.dto.request.user.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserIndexRequest;
import com.bondhub.userservice.dto.request.user.UserUpdateRequest;
import com.bondhub.userservice.dto.response.user.UserImageResponse;
import com.bondhub.userservice.dto.response.user.UserProfileResponse;
import com.bondhub.userservice.dto.response.user.UserResponse;
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

    UserProfileResponse updateBio(BioUpdateRequest request);

    void indexUserToElasticsearch(UserIndexRequest request);

    void deleteUser(String id);
}
