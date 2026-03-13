package com.bondhub.friendservice.dto.response;

import com.bondhub.friendservice.model.enums.FriendStatus;
import lombok.Builder;

@Builder
public record FriendshipStatusResponse(
    Boolean areFriends,
    FriendStatus status,
    String friendshipId,
    String requestedBy 
) {}
