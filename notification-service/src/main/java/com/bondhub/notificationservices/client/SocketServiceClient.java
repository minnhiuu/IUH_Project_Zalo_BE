package com.bondhub.notificationservices.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "socket-service", path = "/internal/presence")
public interface SocketServiceClient {

    @GetMapping("/{userId}/online")
    boolean isUserOnline(@PathVariable("userId") String userId);
}
