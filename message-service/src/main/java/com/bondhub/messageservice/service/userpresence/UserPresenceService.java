package com.bondhub.messageservice.service.userpresence;

import com.bondhub.messageservice.model.ChatUser;

import java.util.List;

public interface UserPresenceService {
    ChatUser saveUser(ChatUser user);

    void disconnect(String userId);

    List<ChatUser> findConnectedUsers();
}
