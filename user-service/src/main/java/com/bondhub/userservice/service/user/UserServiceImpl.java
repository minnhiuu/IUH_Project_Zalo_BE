package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.fileservice.FileUploadResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.client.FileServiceClient;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.AccountResponse;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.dto.response.UserProfileResponse;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.mapper.UserProfileMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserServiceImpl implements UserService {
    final UserRepository userRepository;
    final UserMapper userMapper;
    final AuthServiceClient authServiceClient;
    final SecurityUtil securityUtil;
    final UserProfileMapper userProfileMapper;
    final FileServiceClient fileServiceClient;

    @Value("${aws.s3.bucket.name}")
    String bucketName;

    @Value("${cloud.aws.region.static}")
    String region;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper,
            AuthServiceClient authServiceClient, SecurityUtil securityUtil,
            UserProfileMapper userProfileMapper, FileServiceClient fileServiceClient) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.authServiceClient = authServiceClient;
        this.securityUtil = securityUtil;
        this.userProfileMapper = userProfileMapper;
        this.fileServiceClient = fileServiceClient;
    }

    @Override
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating user with accountId: {}", request.getAccountId());
        User user = userMapper.toUser(request);
        user = userRepository.save(user);
        log.info("User created successfully with id: {}", user.getId());
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
        if (response.getAvatar() != null) {
            response.setAvatar(S3Util.getS3BaseUrl(bucketName, region) + response.getAvatar());
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

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(user, accountResponse);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        if (response.getAvatar() != null) {
            response.setAvatar(baseUrl + response.getAvatar());
        }
        if (response.getBackground() != null) {
            response.setBackground(baseUrl + response.getBackground());
        }
        return response;
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
        UserProfileResponse response = userProfileMapper.toUserProfileResponse(user, accountResponse);
        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        if (response.getAvatar() != null) {
            response.setAvatar(baseUrl + response.getAvatar());
        }
        if (response.getBackground() != null) {
            response.setBackground(baseUrl + response.getBackground());
        }
        return response;
    }

    @Override
    public String updateAvatar(MultipartFile file) {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Updating avatar for user: {}", accountId);

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ApiResponse<FileUploadResponse> response = fileServiceClient
                .uploadFile(file);
        if (response != null && response.data() != null) {
            String key = response.data().getKey();
            user.setAvatar(key);
            userRepository.save(user);
            log.info("Avatar updated successfully for user: {}", accountId);
            return key;
        }

        throw new RuntimeException("Failed to upload avatar");
    }

    @Override
    public String updateBackground(MultipartFile file) {
        String accountId = securityUtil.getCurrentAccountId();
        log.info("Updating background for user: {}", accountId);

        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ApiResponse<FileUploadResponse> response = fileServiceClient
                .uploadFile(file);
        if (response != null && response.data() != null) {
            String key = response.data().getKey();
            user.setBackground(key);
            userRepository.save(user);
            log.info("Background updated successfully for user: {}", accountId);
            return key;
        }

        throw new RuntimeException("Failed to upload background");
    }

    @Override
    public void deleteUser(String id) {
        log.info("Deleting user with id: {}", id);
        if (!userRepository.existsById(id)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        userRepository.deleteById(id);
        log.info("User deleted successfully with id: {}", id);
    }
}
