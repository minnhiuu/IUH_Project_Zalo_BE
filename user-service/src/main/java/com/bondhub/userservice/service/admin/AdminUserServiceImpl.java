package com.bondhub.userservice.service.admin;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.enums.Role;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3Util;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.userservice.client.AuthServiceClient;
import com.bondhub.userservice.dto.response.user.AccountResponse;
import com.bondhub.userservice.dto.response.UserActivityLogResponse;
import com.bondhub.userservice.dto.response.UserAdminDetailResponse;
import com.bondhub.userservice.dto.response.UserAdminResponse;
import com.bondhub.userservice.mapper.AdminUserMapper;
import com.bondhub.userservice.mapper.UserActivityLogMapper;
import com.bondhub.userservice.model.ActivityLogMetadata;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.UserActivityLog;
import com.bondhub.userservice.model.enums.UserAction;
import com.bondhub.userservice.model.enums.UserStatus;
import com.bondhub.userservice.repository.UserActivityLogRepository;
import com.bondhub.userservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    final AdminUserMapper adminUserMapper;
    final UserActivityLogMapper userActivityLogMapper;
    final SecurityUtil securityUtil;

    @Value("${aws.s3.bucket.name}")
    String bucketName;

    @Value("${cloud.aws.region.static}")
    String region;

    @Override
    public PageResponse<List<UserAdminResponse>> getAllUsers(String name, String phone, String email, String status, Pageable pageable) {
        log.info("[Admin] Fetching users - name='{}', phone='{}', email='{}', status='{}'", name, phone, email, status);

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        UserStatus userStatus = UserStatus.fromString(status);

        // Phone or email: exact lookup via auth-service → find user by accountId
        if (StringUtils.hasText(phone) || StringUtils.hasText(email)) {
            AccountResponse account = StringUtils.hasText(phone)
                    ? fetchAccountByPhone(phone)
                    : fetchAccountByEmail(email);

            if (account == null) {
                return PageResponse.fromPage(Page.empty(pageable), u -> null);
            }

            User user = userRepository.findByAccountId(account.id()).orElse(null);
            if (user == null || !matchesStatus(user, userStatus)) {
                return PageResponse.fromPage(Page.empty(pageable), u -> null);
            }

            Page<User> singlePage = new PageImpl<>(List.of(user), pageable, 1);
            return PageResponse.fromPage(singlePage, u -> adminUserMapper.toUserAdminResponse(u, account, baseUrl));
        }

        // Name search in MongoDB with optional status filter
        Page<User> page;
        if (StringUtils.hasText(name)) {
            String keyword = name.trim();
            if (userStatus == UserStatus.BANNED) {
                page = userRepository.findByActiveAndFullNameContainingIgnoreCase(false, keyword, pageable);
            } else if (userStatus == UserStatus.ACTIVE) {
                page = userRepository.findActiveUsersByKeyword(keyword, pageable);
            } else {
                page = userRepository.findByFullNameContainingIgnoreCase(keyword, pageable);
            }
        } else {
            if (userStatus == UserStatus.BANNED) {
                page = userRepository.findByActive(false, pageable);
            } else if (userStatus == UserStatus.ACTIVE) {
                page = userRepository.findActiveUsers(pageable);
            } else {
                page = userRepository.findAll(pageable);
            }
        }

        List<String> accountIds = page.getContent().stream()
                .map(User::getAccountId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, AccountResponse> accountMap = fetchAccountsBatch(accountIds);

        return PageResponse.fromPage(page, user ->
                adminUserMapper.toUserAdminResponse(user, accountMap.get(user.getAccountId()), baseUrl));
    }

    @Override
    public UserAdminDetailResponse getUserDetail(String userId) {
        log.info("[Admin] Fetching user detail for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
        AccountResponse account = fetchAccountSafe(user.getAccountId());
        long totalLogs = activityLogRepository.countByUserId(userId);

        String banReason = null;
        if (!user.isActive()) {
            banReason = activityLogRepository
                    .findTopByUserIdAndActionOrderByCreatedAtDesc(userId, UserAction.ACCOUNT_LOCKED)
                    .map(log -> log.getMetadata() != null ? log.getMetadata().reason() : null)
                    .orElse(null);
        }

        return adminUserMapper.toUserAdminDetailResponse(user, account, baseUrl, totalLogs, banReason);
    }

    @Override
    public PageResponse<List<UserActivityLogResponse>> getUserActivityLogs(String userId, Pageable pageable) {
        log.info("[Admin] Fetching activity logs for userId={}", userId);

        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        Page<UserActivityLog> page = activityLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.fromPage(page, userActivityLogMapper::toUserActivityLogResponse);
    }

    @Override
    public void banUser(String userId, String reason) {
        log.info("[Admin] Banning user userId={}, reason={}", userId, reason);
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            if (userId.equals(securityUtil.getCurrentUserId())) {
                throw new AppException(ErrorCode.CANNOT_BAN_YOURSELF);
            }

            AccountResponse targetAccount = fetchAccountSafe(user.getAccountId());
            if (targetAccount != null && Role.ADMIN.name().equals(targetAccount.role())) {
                throw new AppException(ErrorCode.CANNOT_BAN_ADMIN);
            }

            authServiceClient.banAccount(user.getAccountId(), reason);

            user.setActive(false);
            userRepository.save(user);

            activityLogRepository.save(UserActivityLog.builder()
                    .userId(userId)
                    .action(UserAction.ACCOUNT_LOCKED)
                    .description("Account banned by admin. Reason: " + reason)
                    .metadata(ActivityLogMetadata.builder()
                            .reason(reason)
                            .accountId(user.getAccountId())
                            .build())
                    .build());

            log.info("[Admin] User banned successfully: userId={}", userId);
        } catch (AppException e) {
            log.error("[Admin] AppException in banUser userId={}: code={}", userId, e.getErrorCode());
            throw e;
        } catch (Exception e) {
            log.error("[Admin] Unexpected error in banUser userId={}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void unbanUser(String userId) {
        log.info("[Admin] Unbanning user userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        authServiceClient.unbanAccount(user.getAccountId());

        user.setActive(true);
        userRepository.save(user);

        activityLogRepository.save(UserActivityLog.builder()
                .userId(userId)
                .action(UserAction.ACCOUNT_UNLOCKED)
                .description("Account unbanned by admin.")
                .metadata(ActivityLogMetadata.builder()
                        .accountId(user.getAccountId())
                        .build())
                .build());

        log.info("[Admin] User unbanned successfully: userId={}", userId);
    }

    private boolean matchesStatus(User user, UserStatus userStatus) {
        if (userStatus == null) return true;
        return switch (userStatus) {
            case ACTIVE -> user.isActive();
            case BANNED -> !user.isActive();
        };
    }

    private AccountResponse fetchAccountSafe(String accountId) {
        if (!StringUtils.hasText(accountId)) return null;
        try {
            ApiResponse<AccountResponse> response = authServiceClient.getAccountById(accountId);
            return (response != null) ? response.data() : null;
        } catch (Exception e) {
            log.warn("[Admin] Failed to fetch account for accountId={}: {}", accountId, e.getMessage());
            return null;
        }
    }

    private AccountResponse fetchAccountByPhone(String phone) {
        try {
            ApiResponse<AccountResponse> response = authServiceClient.getAccountByPhoneNumber(phone);
            return (response != null) ? response.data() : null;
        } catch (Exception e) {
            log.warn("[Admin] Failed to fetch account by phone={}: {}", phone, e.getMessage());
            return null;
        }
    }

    private AccountResponse fetchAccountByEmail(String email) {
        try {
            ApiResponse<AccountResponse> response = authServiceClient.getAccountByEmail(email);
            return (response != null) ? response.data() : null;
        } catch (Exception e) {
            log.warn("[Admin] Failed to fetch account by email={}: {}", email, e.getMessage());
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
