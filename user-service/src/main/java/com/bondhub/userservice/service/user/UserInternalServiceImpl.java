package com.bondhub.userservice.service.user;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.S3Util;
import com.bondhub.userservice.dto.response.UserSyncResponse;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@Transactional(readOnly = true)
public class UserInternalServiceImpl implements UserInternalService {

    final UserRepository userRepository;
    final UserMapper userMapper;

    @Value("${aws.s3.bucket.name}")
    String bucketName;

    @Value("${cloud.aws.region.static}")
    String region;

    @Override
    public UserSummaryResponse getUserSummaryByAccountId(String accountId) {
        log.info("Internal: Fetching user summary for accountId: {}", accountId);
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserSummaryResponse response = userMapper.toUserSummaryResponse(user);
        if (response.avatar() != null) {
            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);
            return UserSummaryResponse.builder()
                    .id(response.id())
                    .fullName(response.fullName())
                    .avatar(baseUrl + response.avatar())
                    .build();
        }
        return response;
    }

    @Override
    public long getUserCount() {
        return userRepository.count();
    }

    @Override
    public UserSyncResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return mapToSyncResponse(user);
    }

    @Override
    public List<UserSyncResponse> getUsersBatch(String lastId, int size) {
        List<User> users;
        PageRequest pageRequest = PageRequest.of(0, size);
        
        if (lastId == null || lastId.isEmpty()) {
            users = userRepository.findAllByOrderByIdAsc(pageRequest);
        } else {
            users = userRepository.findByIdGreaterThanOrderByIdAsc(lastId, pageRequest);
        }

        return users.stream()
                .map(this::mapToSyncResponse)
                .toList();
    }

    @Override
    public void recordLastLogin(String accountId) {
        userRepository.findByAccountId(accountId).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            log.debug("Recorded last login for accountId={}, userId={}", accountId, user.getId());
        });
    }

    @Override
    public void syncBanStatus(String accountId, boolean banned) {
        userRepository.findByAccountId(accountId).ifPresent(user -> {
            user.setActive(!banned);
            userRepository.save(user);
            log.info("Synced ban status for accountId={}, banned={}", accountId, banned);
        });
    }

    private UserSyncResponse mapToSyncResponse(User user) {
        return UserSyncResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatar(user.getAvatar())
                .accountId(user.getAccountId())
                .build();
    }
}
