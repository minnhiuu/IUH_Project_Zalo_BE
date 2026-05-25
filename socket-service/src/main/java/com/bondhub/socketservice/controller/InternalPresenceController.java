package com.bondhub.socketservice.controller;

import com.bondhub.common.enums.Status;
import com.bondhub.socketservice.model.ChatUser;
import com.bondhub.socketservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/batch-online")
    public List<String> getOnlineUsers(@RequestBody List<String> userIds) {
        return repository.findByIdInAndStatus(userIds, Status.ONLINE).stream()
                .map(ChatUser::getId)
                .toList();
    }
}
