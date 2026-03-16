package com.bondhub.friendservice.dto.response;

import com.bondhub.friendservice.model.enums.FriendStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record FriendRequestResponse(
    String id,
    String requestedUserId,
    String requestedUserName,
    String requestedUserAvatar,
    String receivedUserId,
    String receivedUserName,
    String receivedUserAvatar,
    String message,
    FriendStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
