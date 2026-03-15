package com.bondhub.messageservice.service.userpresence;

import com.bondhub.common.enums.Status;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.messageservice.client.FriendServiceClient;
import com.bondhub.messageservice.dto.response.PresenceEvent;
import com.bondhub.messageservice.model.ChatUser;
import com.bondhub.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPresenceServiceImpl implements UserPresenceService {

    private final ChatUserRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FriendServiceClient friendServiceClient;

    @Override
    public ChatUser saveUser(ChatUser user) {
        ChatUser savedUser = repository.findById(user.getId())
                .map(storedUser -> {
                    storedUser.setStatus(Status.ONLINE);
                    storedUser.setFullName(user.getFullName());
                    storedUser.setEmail(user.getEmail());
                    log.info("[Presence] User ONLINE: {}", storedUser.getEmail());
                    return repository.save(storedUser);
                })
                .orElseGet(() -> {
                    user.setStatus(Status.ONLINE);
                    log.info("[Presence] New user ONLINE: {}", user.getEmail());
                    return repository.save(user);
                });

        // Cold start check
        if (savedUser.getFriendIds() == null || savedUser.getFriendIds().isEmpty()) {
            try {
                ApiResponse<Set<String>> response = friendServiceClient.getFriendIds(savedUser.getId());
                if (response != null && response.data() != null && !response.data().isEmpty()) {
                    savedUser.setFriendIds(response.data());
                    savedUser = repository.save(savedUser);
                    log.info("[Presence] Synced {} friend IDs for user {} from friend-service.", response.data().size(),
                            savedUser.getId());
                }
            } catch (Exception e) {
                log.warn("[Presence] Failed to sync friend IDs for user {}", savedUser.getId(), e);
            }
        }

        notifyFriendsAboutPresence(savedUser, Status.ONLINE);
        return savedUser;
    }

    @Override
    public void disconnect(String userId) {
        repository.findById(userId).ifPresent(user -> {
            user.setStatus(Status.OFFLINE);
            repository.save(user);
            log.info("[Presence] User OFFLINE: {}", user.getEmail());

            notifyFriendsAboutPresence(user, Status.OFFLINE);
        });
    }

    private void notifyFriendsAboutPresence(ChatUser user, Status status) {
        if (user.isInvisible() || user.getFriendIds() == null || user.getFriendIds().isEmpty()) {
            return;
        }

        List<ChatUser> onlineFriends = repository.findByIdInAndStatus(user.getFriendIds(), Status.ONLINE);

        // Notify each online friend about this user's new status
        PresenceEvent event = new PresenceEvent(user.getId(), status);
        for (ChatUser friend : onlineFriends) {
            messagingTemplate.convertAndSendToUser(
                    friend.getId(),
                    "/queue/presence",
                    event);
        }

        // If user is coming online, also send them the current list of online friends
        if (status == Status.ONLINE) {
            for (ChatUser friend : onlineFriends) {
                messagingTemplate.convertAndSendToUser(
                        user.getId(),
                        "/queue/presence",
                        new PresenceEvent(friend.getId(), Status.ONLINE));
            }
        }
    }

    @Override
    public List<ChatUser> findConnectedUsers() {
        return repository.findAllByStatus(Status.ONLINE);
    }
}
