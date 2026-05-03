package com.bondhub.socketservice.controller;

import com.bondhub.common.enums.Status;
import com.bondhub.socketservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/presence")
@RequiredArgsConstructor
public class InternalPresenceController {

    private final ChatUserRepository repository;

    @GetMapping("/{userId}/online")
    public boolean isUserOnline(@PathVariable String userId) {
        return repository.findById(userId)
                .map(user -> user.getStatus() == Status.ONLINE)
                .orElse(false);
    }
}
