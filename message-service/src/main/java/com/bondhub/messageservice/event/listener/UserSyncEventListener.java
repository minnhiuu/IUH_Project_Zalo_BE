package com.bondhub.messageservice.event.listener;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.utils.S3UrlUtil;
import com.bondhub.common.utils.S3Util;
import com.bondhub.messageservice.client.UserServiceClient;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSyncEventListener {

    private final UserServiceClient userServiceClient;
    private final ChatUserRepository chatUserRepository;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Async
    @EventListener
    public void handleUserSyncEvent(UserSyncEvent event) {
        String userId = event.getUserId();

        Optional<ChatUser> existingUserOpt = chatUserRepository.findById(userId);
        if (existingUserOpt.isPresent()) {
            ChatUser existing = existingUserOpt.get();
            if (existing.getFullName() != null && !existing.getFullName().isBlank()) {
                return;
            }
        }

        try {
            log.info("❄️ Cold Start Async: Fetching user {} from user-service", userId);
            UserSummaryResponse userDto = userServiceClient.getUserById(userId).data();

            if (userDto == null) {
                log.warn("⚠️ User-service returned null data for user {}", userId);
                return;
            }

            ChatUser mirrorUser = existingUserOpt.orElseGet(() ->
                    ChatUser.builder().id(userDto.id() != null ? userDto.id() : userId).build());

            String baseUrl = S3Util.getS3BaseUrl(bucketName, region);

            if (userDto.fullName() != null && !userDto.fullName().isBlank()) {
                mirrorUser.setFullName(userDto.fullName());
            }
            if (userDto.avatar() != null && !userDto.avatar().isBlank()) {
                mirrorUser.setAvatar(S3UrlUtil.extractStorageKey(userDto.avatar(), baseUrl));

                log.info("✅ Updated ChatUser mirror22222222222 with avatar: {}", mirrorUser.getAvatar());
            }
            mirrorUser.setLastUpdatedAt(LocalDateTime.now());

            chatUserRepository.save(mirrorUser);
            log.info("✅ Successfully synced and saved user {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to fetch user {} from user-service: {}", userId, e.getMessage());
        }
    }
}
