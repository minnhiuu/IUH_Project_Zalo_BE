package com.bondhub.userservice.service.admin;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3Util;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.dto.response.AccountResponse;
import com.bondhub.userservice.dto.response.AuditResponse;
import com.bondhub.userservice.dto.response.UserActivityLogResponse;
import com.bondhub.userservice.dto.response.UserAdminDetailResponse;
import com.bondhub.userservice.dto.response.UserAdminResponse;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.UserActivityLog;
import com.bondhub.userservice.model.enums.UserAction;
import com.bondhub.userservice.repository.UserActivityLogRepository;
import com.bondhub.userservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserServiceImpl implements AdminUserService {

    final UserRepository userRepository;
    final UserActivityLogRepository activityLogRepository;
    final AuthServiceClient authServiceClient;

    @Value("${aws.s3.bucket.name}")
    String bucketName;

    @Value("${cloud.aws.region.static}")
    String region;

    @Override
    public PageResponse<List<UserAdminResponse>> getAllUsers(String search, String status, Pageable pageable) {
        log.info("[Admin] Fetching all users — search='{}', status='{}'", search, status);

        String keyword = (search != null && !search.isBlank()) ? search.trim() : null;

        // Build filtered page based on keyword and/or status
        // "ACTIVE" = active != false
        // "BANNED" = active = false
        Boolean isBanned = (status != null && status.equalsIgnoreCase("BANNED")) ? Boolean.TRUE : null;
        boolean filterActive = (status != null && status.equalsIgnoreCase("ACTIVE"));

        Page<User> page;
        if (keyword != null && isBanned != null) {
            // banned + keyword
            page = userRepository.findByActiveAndFullNameContainingIgnoreCase(false, keyword, pageable);
        } else if (keyword != null && filterActive) {
            // active + keyword
            page = userRepository.findActiveUsersByKeyword(keyword, pageable);
        } else if (keyword != null) {
            page = userRepository.findByFullNameContainingIgnoreCase(keyword, pageable);
        } else if (isBanned != null) {
            page = userRepository.findByActive(false, pageable);
        } else if (filterActive) {
            page = userRepository.findActiveUsers(pageable);
        } else {
            page = userRepository.findAll(pageable);
        }

        // Batch fetch all account info for the page in a single Feign call
        List<String> accountIds = page.getContent().stream()
                .map(User::getAccountId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, AccountResponse> accountMap = fetchAccountsBatch(accountIds);

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

        return PageResponse.fromPage(page, user -> {
            AccountResponse accountInfo = accountMap.get(user.getAccountId());

            UserResponse userResponse = UserResponse.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .dob(user.getDob())
                    .bio(user.getBio())
                    .gender(user.getGender())
                    .accountInfo(accountInfo)
                    .avatar(user.getAvatar() != null ? baseUrl + user.getAvatar() : null)
                    .background(user.getBackground() != null ? baseUrl + user.getBackground() : null)
                    .backgroundY(user.getBackgroundY())
                    .build();

            AuditResponse auditResponse = AuditResponse.builder()
                    .createdAt(user.getCreatedAt())
                    .lastModifiedAt(user.getLastModifiedAt())
                    .createdBy(user.getCreatedBy())
                    .lastModifiedBy(user.getLastModifiedBy())
                    .lastLoginAt(user.getLastLoginAt())
                    .active(user.isActive())
                    .build();

            return UserAdminResponse.builder()
                    .user(userResponse)
                    .audit(auditResponse)
                    .build();
        });
    }

    @Override
    public UserAdminDetailResponse getUserDetail(String userId) {
        log.info("[Admin] Fetching user detail for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        AccountResponse account = fetchAccountSafe(user.getAccountId());
        long totalLogs = activityLogRepository.countByUserId(userId);

        return UserAdminDetailResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .dob(user.getDob())
                .bio(user.getBio())
                .gender(user.getGender())
                .avatar(user.getAvatar() != null ? baseUrl + user.getAvatar() : null)
                .background(user.getBackground() != null ? baseUrl + user.getBackground() : null)
                .backgroundY(user.getBackgroundY())
                // Account info
                .accountId(user.getAccountId())
                .email(account != null ? account.email() : null)
                .phoneNumber(account != null ? account.phoneNumber() : null)
                .role(account != null ? account.role() : null)
                .active(account != null ? account.enabled() : null)
                .isVerified(account != null ? account.isVerified() : null)
                // Audit info
                .createdAt(user.getCreatedAt())
                .lastModifiedAt(user.getLastModifiedAt())
                .createdBy(user.getCreatedBy())
                .lastModifiedBy(user.getLastModifiedBy())
                .lastLoginAt(user.getLastLoginAt())
                .totalActivityLogs(totalLogs)
                .build();
    }

    @Override
    public PageResponse<List<UserActivityLogResponse>> getUserActivityLogs(String userId, Pageable pageable) {
        log.info("[Admin] Fetching activity logs for userId={}", userId);

        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        Page<UserActivityLog> page = activityLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return PageResponse.fromPage(page, log -> UserActivityLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .action(log.getAction())
                .description(log.getDescription())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .createdBy(log.getCreatedBy())
                .build());
    }

    @Override
    public void banUser(String userId, String reason) {
        log.info("[Admin] Banning user userId={}, reason={}", userId, reason);
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            log.info("[Admin] Found user={}, accountId={}", user.getId(), user.getAccountId());

            authServiceClient.banAccount(user.getAccountId(), reason);
            log.info("[Admin] Auth-service banAccount called successfully for accountId={}", user.getAccountId());

            // Sync active flag locally for native status filtering
            user.setActive(false);
            userRepository.save(user);
            log.info("[Admin] User.active synced to false for userId={}", userId);

            activityLogRepository.save(UserActivityLog.builder()
                    .userId(userId)
                    .action(UserAction.ACCOUNT_LOCKED)
                    .description("Account banned by admin. Reason: " + reason)
                    .metadata(Map.of("reason", reason, "accountId", user.getAccountId()))
                    .build());

            log.info("[Admin] User banned successfully: userId={}", userId);
        } catch (AppException e) {
            log.error("[Admin] AppException in banUser userId={}: code={}", userId, e.getErrorCode());
            throw e;
        } catch (Exception e) {
            log.error("[Admin] Unexpected error in banUser userId={}: {} - {}", userId, e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void unbanUser(String userId) {
        log.info("[Admin] Unbanning user userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        authServiceClient.unbanAccount(user.getAccountId());

        // Sync enabled flag locally for native status filtering
        user.setActive(true);
        userRepository.save(user);

        activityLogRepository.save(UserActivityLog.builder()
                .userId(userId)
                .action(UserAction.ACCOUNT_UNLOCKED)
                .description("Account unbanned by admin.")
                .metadata(Map.of("accountId", user.getAccountId()))
                .build());

        log.info("[Admin] User unbanned successfully: userId={}", userId);
    }

    private AccountResponse fetchAccountSafe(String accountId) {
        if (!StringUtils.hasText(accountId)) return null;
        try {
            ApiResponse<AccountResponse> response = authServiceClient.getAccountById(accountId);
            return (response != null) ? response.data() : null;
        } catch (Exception e) {
            log.warn("[Admin] Failed to fetch account info for accountId={}: {}", accountId, e.getMessage());
            return null;
        }
    }

    private Map<String, AccountResponse> fetchAccountsBatch(List<String> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        try {
            ApiResponse<List<AccountResponse>> response = authServiceClient.getAccountsByIds(accountIds);
            if (response != null && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(AccountResponse::id, a -> a));
            }
        } catch (Exception e) {
            log.warn("[Admin] Batch account fetch failed: {}", e.getMessage());
        }
        return Map.of();
    }

}
