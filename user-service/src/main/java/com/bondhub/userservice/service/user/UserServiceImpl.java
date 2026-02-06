package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.client.FileServiceClient;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.request.UserIndexRequest;
import com.bondhub.userservice.dto.request.AvatarUpdateRequest;
import com.bondhub.userservice.dto.request.BackgroundUpdateRequest;
import com.bondhub.userservice.dto.response.AccountResponse;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.dto.response.UserProfileResponse;
import com.bondhub.userservice.dto.response.UserImageResponse;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.mapper.UserProfileMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    final UserRepository userRepository;
    final UserMapper userMapper;
    final AuthServiceClient authServiceClient;
    final SecurityUtil securityUtil;
    final UserProfileMapper userProfileMapper;
    final FileServiceClient fileServiceClient;
    final UserSearchService userSearchService;

    @Value("${aws.s3.bucket.name}")
    String bucketName;

    @Value("${cloud.aws.region.static}")
    String region;

    @Override
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating user with accountId: {}", request.accountId());
        User user = userMapper.toUser(request);
        user = userRepository.save(user);
        log.info("User created successfully with id: {}", user.getId());

        syncUserToIndex(user, null, null);

        return userMapper.toUserResponse(user);
    }

    @Override
    public UserResponse getUserById(String id) {
        log.info("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    @Override
    public UserResponse getUserByAccountId(String accountId) {
        log.info("Fetching user with accountId: {}", accountId);
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    @Override
    public UserSummaryResponse getUserSummaryByAccountId(String accountId) {
        log.info("Fetching user summary with accountId: {}", accountId);
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserSummaryResponse response = userMapper.toUserSummaryResponse(user);
        if (response.avatar() != null) {
            return UserSummaryResponse.builder()
                    .id(response.id())
                    .fullName(response.fullName())
                    .avatar(S3Util.getS3BaseUrl(bucketName, region) + response.avatar())
                    .build();
        }
        return response;
    }

    @Override
    public UserProfileResponse getMyUserWithAccountInfo() {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Fetching detailed profile for account id: {}", accountId);

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        AccountResponse accountResponse = null;
        try {
            ApiResponse<AccountResponse> accountApiResponse = authServiceClient.getAccountById(accountId);
            if (accountApiResponse != null && accountApiResponse.data() != null) {
                accountResponse = accountApiResponse.data();
                log.info("Account info fetched successfully for profile: {}", accountId);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch account info for profile: {}. Resulting profile will have missing contact info.",
                    accountId, e);
        }

        return getUserProfileResponseWithUrl(user, accountResponse);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll().stream()
                .map(userMapper::toUserResponse)
                .toList();
    }

    @Override
    public UserProfileResponse updateUser(UserUpdateRequest request) {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Updating user profile for account: {}", accountId);

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        userMapper.updateUserFromRequest(user, request);
        user = userRepository.save(user);

        AccountResponse accountResponse = null;
        try {
            ApiResponse<AccountResponse> accountApiResponse = authServiceClient.getAccountById(accountId);
            if (accountApiResponse != null && accountApiResponse.data() != null) {
                accountResponse = accountApiResponse.data();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch account info for updated user: {}", accountId, e);
        }

        log.info("User profile updated successfully for account: {}", accountId);

        syncUserToIndex(user, accountResponse != null ? accountResponse.phoneNumber() : null, null);

        return getUserProfileResponseWithUrl(user, accountResponse);
    }

    private UserProfileResponse getUserProfileResponseWithUrl(User user, AccountResponse accountResponse) {
        UserProfileResponse response = userProfileMapper.toUserProfileResponse(user, accountResponse);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        
        return UserProfileResponse.builder()
                .id(response.id())
                .phoneNumber(response.phoneNumber())
                .email(response.email())
                .fullName(response.fullName())
                .bio(response.bio())
                .gender(response.gender())
                .dob(response.dob())
                .avatar(response.avatar() != null ? baseUrl + response.avatar() : null)
                .background(response.background() != null ? baseUrl + response.background() : null)
                .backgroundY(response.backgroundY())
                .build();
    }

    @Override
    public UserImageResponse updateAvatar(AvatarUpdateRequest request) {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Updating avatar for user: {}", accountId);

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String oldAvatarKey = user.getAvatar();

        ApiResponse<FileUploadResponse> response = fileServiceClient
                .uploadFile(request.file());
        if (response != null && response.data() != null) {
            String key = response.data().key();
            user.setAvatar(key);
            userRepository.save(user);

            if (oldAvatarKey != null && !oldAvatarKey.isEmpty()) {
                try {
                    fileServiceClient.deleteFile(oldAvatarKey);
                    log.info("Old avatar deleted successfully: {}", oldAvatarKey);
                } catch (Exception e) {
                    log.error("Failed to delete old avatar: {}", oldAvatarKey, e);
                }
            }

            log.info("Avatar updated successfully for user: {}", accountId);

            syncUserToIndex(user, null, null);

            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
            return userMapper.toAvatarResponse(user, baseUrl);
        }

        throw new RuntimeException("Failed to upload avatar");
    }

    @Override
    public UserImageResponse updateBackground(BackgroundUpdateRequest request) {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Updating background for user: {} with y: {}", accountId, request.y());

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String oldBackgroundKey = user.getBackground();

        ApiResponse<FileUploadResponse> response = fileServiceClient
                .uploadFile(request.file());
        if (response != null && response.data() != null) {
            String key = response.data().key();
            user.setBackground(key);
            user.setBackgroundY(request.y());
            userRepository.save(user);

            if (oldBackgroundKey != null && !oldBackgroundKey.isEmpty()) {
                try {
                    fileServiceClient.deleteFile(oldBackgroundKey);
                    log.info("Old background deleted successfully: {}", oldBackgroundKey);
                } catch (Exception e) {
                    log.error("Failed to delete old background: {}", oldBackgroundKey, e);
                }
            }

            log.info("Background updated successfully for user: {}", accountId);
            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
            return userMapper.toBackgroundResponse(user, baseUrl);
        }

        throw new RuntimeException("Failed to upload background");
    }

    @Override
    public UserImageResponse updateBackgroundPosition(Double y) {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Updating background position for user: {} to y: {}", accountId, y);

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getBackground() == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        user.setBackgroundY(y);
        userRepository.save(user);

        log.info("Background position updated successfully for user: {}", accountId);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        return userMapper.toBackgroundResponse(user, baseUrl);
    }

    @Override
    public void deleteUser(String id) {
        log.info("Deleting user with id: {}", id);
        if (!userRepository.existsById(id)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        userRepository.deleteById(id);
        log.info("User deleted successfully with id: {}", id);

        // Delete from Elasticsearch
        try {
            userSearchService.deleteFromIndex(id);
        } catch (Exception e) {
            log.error("Failed to delete user from Elasticsearch index: {}", id, e);
        }
    }

    @Override
    public void indexUserToElasticsearch(UserIndexRequest request) {
        log.info("Indexing user to Elasticsearch: userId={}, phoneNumber={}, role={}", 
                request.userId(), request.phoneNumber(), request.role());
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        syncUserToIndex(user, request.phoneNumber(), request.role());
    }

    private void syncUserToIndex(User user, String phoneNumber, Role role) {
        try {
            UserIndex userIndex = UserIndex.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .phoneNumber(phoneNumber)
                    .accountId(user.getAccountId())
                    .role(role != null ? role.name() : Role.USER.name())
                    .avatar(user.getAvatar())
                    .createdAt(user.getCreatedAt())
                    .build();
            userSearchService.saveToToIndex(userIndex);
        } catch (Exception e) {
            log.error("Failed to sync user to Elasticsearch index: {}", user.getId(), e);
        }
    }
}
