package com.bondhub.messageservice.event.listener;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.utils.S3Util;
import com.bondhub.messageservice.client.UserServiceClient;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import com.bondhub.common.utils.S3UtilV2;
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

    private final S3UtilV2 s3UtilV2;

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

            if (userDto.avatar() != null && !userDto.avatar().isBlank()) {
                mirrorUser.setAvatar(s3UtilV2.extractStorageKey(userDto.avatar()));

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
