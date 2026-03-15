package com.bondhub.friendservice.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record FriendResponse(
    String userId,
    String userName,
    String userAvatar,
    String userEmail,
    String userPhone,
    LocalDateTime friendsSince,
    Integer mutualFriendsCount
) {}
