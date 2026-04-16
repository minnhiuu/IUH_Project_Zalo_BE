package com.bondhub.socketservice.controller;

import com.bondhub.socketservice.model.ChatUser;
import com.bondhub.socketservice.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/presence")
public class UserPresenceController {

    private final UserPresenceService userPresenceService;

    @MessageMapping("/user.addUser")
    public void addUser(
            @Payload ChatUser user,
            SimpMessageHeaderAccessor headerAccessor) {
        ChatUser savedUser = userPresenceService.saveUser(user);
        headerAccessor.getSessionAttributes().put("userId", savedUser.getId());
    }

    @MessageMapping("/user.disconnectUser")
    public void disconnectUser(@Payload ChatUser user) {
        userPresenceService.disconnect(user.getId());
    }
}
