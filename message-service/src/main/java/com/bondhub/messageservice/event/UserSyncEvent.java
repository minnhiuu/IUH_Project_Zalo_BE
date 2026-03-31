package com.bondhub.messageservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSyncEvent {
    private final String userId;
}
