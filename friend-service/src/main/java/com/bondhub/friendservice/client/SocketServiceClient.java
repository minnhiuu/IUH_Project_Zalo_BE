package com.bondhub.friendservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "socket-service", path = "/internal/presence")
public interface SocketServiceClient {
    @PostMapping("/batch-online")
    List<String> getOnlineUsers(@RequestBody List<String> userIds);
}
