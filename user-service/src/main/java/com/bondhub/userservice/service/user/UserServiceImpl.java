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
import com.bondhub.userservice.dto.request.BioUpdateRequest;
import com.bondhub.userservice.dto.request.elasticsearch.UserIndexRequest;
import com.bondhub.userservice.dto.request.user.UserCreateRequest;
import com.bondhub.userservice.dto.request.user.UserUpdateRequest;
import com.bondhub.userservice.dto.request.user.AvatarUpdateRequest;
import com.bondhub.userservice.dto.request.user.BackgroundUpdateRequest;
import com.bondhub.userservice.dto.response.user.AccountResponse;
import com.bondhub.userservice.dto.response.user.UserResponse;
import com.bondhub.userservice.dto.response.user.UserProfileResponse;
import com.bondhub.userservice.dto.response.user.UserImageResponse;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.mapper.UserProfileMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.publisher.UserIndexEventPublisher;
import com.bondhub.userservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

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
    final UserIndexEventPublisher userIndexEventPublisher;

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

        publishUserIndexEvent(user, request.phoneNumber(), request.role());

        return userMapper.toUserResponse(user);
    }

    private void publishUserIndexEvent(User user, String phoneNumber, String role) {
        userIndexEventPublisher.publishIndexRequest(UserIndexRequest.builder()
                .userId(user.getId())
                .accountId(user.getAccountId())
                .fullName(user.getFullName())
                .avatar(user.getAvatar())
                .phoneNumber(phoneNumber)
                .role(role != null ? Role.valueOf(role) : null)
                .build());
    }

    private void publishUserIndexEvent(User user, AccountResponse accountResponse) {
        publishUserIndexEvent(user,
                accountResponse != null ? accountResponse.phoneNumber() : null,
                accountResponse != null ? accountResponse.role() : null);
    }

    @Override
    public UserResponse getUserById(String id) {
        log.info("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        AccountResponse accountResponse = null;
        try {
            ApiResponse<AccountResponse> accountApiResponse = authServiceClient.getAccountById(user.getAccountId());
            if (accountApiResponse != null && accountApiResponse.data() != null) {
                accountResponse = accountApiResponse.data();
                log.info("Account info fetched successfully for user: {}", id);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch account info for user: {}. Account info will be null.", id, e);
        }

        return getUserResponseWithUrl(user, accountResponse);
    }

    @Override
    public UserResponse getUserByAccountId(String accountId) {
        log.info("Fetching user with accountId: {}", accountId);
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        AccountResponse accountResponse = null;
        try {
            ApiResponse<AccountResponse> accountApiResponse = authServiceClient.getAccountById(accountId);
            if (accountApiResponse != null && accountApiResponse.data() != null) {
                accountResponse = accountApiResponse.data();
                log.info("Account info fetched successfully for accountId: {}", accountId);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch account info for accountId: {}. Account info will be null.", accountId, e);
        }

        return getUserResponseWithUrl(user, accountResponse);
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
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return userRepository.findAll().stream()
                .map(user -> {
                    UserResponse response = userMapper.toUserResponse(user);
                    return UserResponse.builder()
                            .id(response.id())
                            .fullName(response.fullName())
                            .dob(response.dob())
                            .bio(response.bio())
                            .gender(response.gender())
                            .accountInfo(response.accountInfo())
                            .avatar(response.avatar() != null ? baseUrl + response.avatar() : null)
                            .background(response.background() != null ? baseUrl + response.background() : null)
                            .backgroundY(response.backgroundY())
                            .build();
                })
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

        publishUserIndexEvent(user, accountResponse);

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
                .role(accountResponse != null ? accountResponse.role() : null)
                .build();
    }

    private UserResponse getUserResponseWithUrl(User user, AccountResponse accountResponse) {
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .dob(user.getDob())
                .bio(user.getBio())
                .gender(user.getGender())
                .accountInfo(accountResponse)
                .avatar(user.getAvatar() != null ? baseUrl + user.getAvatar() : null)
                .background(user.getBackground() != null ? baseUrl + user.getBackground() : null)
                .backgroundY(user.getBackgroundY())
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

            publishUserIndexEvent(user, null);

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

            publishUserIndexEvent(user, null);

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
    public UserProfileResponse updateBio(BioUpdateRequest request) {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Updating bio for user: {}", accountId);

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setBio(request.bio());
        user = userRepository.save(user);

        AccountResponse accountResponse = null;
        try {
            ApiResponse<AccountResponse> accountApiResponse = authServiceClient.getAccountById(accountId);
            if (accountApiResponse != null && accountApiResponse.data() != null) {
                accountResponse = accountApiResponse.data();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch account info for user: {}", accountId, e);
        }

        log.info("Bio updated successfully for user: {}", accountId);

        return getUserProfileResponseWithUrl(user, accountResponse);
    }

    @Override
    public void deleteUser(String id) {
        log.info("Deleting user with id: {}", id);
        if (!userRepository.existsById(id)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        userRepository.deleteById(id);
        log.info("User deleted successfully with id: {}", id);

        try {
            userIndexEventPublisher.publishDeleteRequest(id);
        } catch (Exception e) {
            log.error("Failed to delete user from Elasticsearch index: {}", id, e);
        }
    }

    @Override
    public Map<String, UserSummaryResponse> getUsersByIds(List<String> userIds) {
        log.info("Fetching batch users: {}", userIds);
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        List<User> users = userRepository.findAllById(userIds);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return users.stream().collect(Collectors.toMap(
                User::getId,
                user -> UserSummaryResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .avatar(user.getAvatar() != null ? baseUrl + user.getAvatar() : null)
                        .build()
        ));
    }
}
