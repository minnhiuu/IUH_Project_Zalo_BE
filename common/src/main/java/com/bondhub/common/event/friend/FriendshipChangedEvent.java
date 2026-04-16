package com.bondhub.common.event.friend;

import com.bondhub.common.enums.FriendshipAction;
import lombok.Builder;

@Builder
public record FriendshipChangedEvent(
    String userA,
    String userB,
    String friendshipId,
    FriendshipAction action,
    Long timestamp
) {}
