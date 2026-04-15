package com.bondhub.friendservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FriendRequestSendRequest(
    @NotBlank(message = "{friend.request.receiverId.notBlank}")
    String receiverId,
    
    String message
) {}
