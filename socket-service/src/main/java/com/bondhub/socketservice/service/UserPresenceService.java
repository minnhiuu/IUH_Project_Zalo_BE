package com.bondhub.socketservice.service;

import com.bondhub.socketservice.model.ChatUser;

import java.util.List;

public interface UserPresenceService {
    ChatUser saveUser(ChatUser user);
    void disconnect(String userId);
    List<ChatUser> findConnectedUsers();
}
