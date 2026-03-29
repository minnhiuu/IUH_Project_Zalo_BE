package com.bondhub.messageservice.event.listener;

import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.messageservice.client.UserServiceClient;
import com.bondhub.messageservice.event.UserSyncEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSyncEventListener {

    private final UserServiceClient userServiceClient;
    private final ChatUserRepository chatUserRepository;

    @Async
    @EventListener
    public void handleUserSyncEvent(UserSyncEvent event) {
        String userId = event.getUserId();
        
        // Check again to avoid duplicate fetches if multiple threads triggered it
        if (chatUserRepository.existsById(userId)) {
            return;
        }

        try {
            log.info("❄️ Cold Start Async: Fetching user {} from user-service", userId);
            UserSummaryResponse userDto = userServiceClient.getUserById(userId).data();
            
            ChatUser mirrorUser = ChatUser.builder()
                    .id(userDto.id())
                    .fullName(userDto.fullName())
                    .avatar(userDto.avatar())
                    .lastUpdatedAt(LocalDateTime.now())
                    .build();
                    
            chatUserRepository.save(mirrorUser);
            log.info("✅ Successfully synced and saved user {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to fetch user {} from user-service: {}", userId, e.getMessage());
        }
    }
}
