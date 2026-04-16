package com.bondhub.common.dto.client.socketservice;

import com.bondhub.common.enums.SocketEventType;

public record SocketEvent(
        SocketEventType type,

        // Target userId to push to. null = broadcast */
        String targetUserId,

        // /queue/messages, /queue/conversations, /topic/group.{id} */
        String destination,

        Object payload
) {}
