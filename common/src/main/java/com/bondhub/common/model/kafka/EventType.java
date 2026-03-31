package com.bondhub.common.model.kafka;

public enum EventType {
    // Account Events (auth-service)
    ACCOUNT_REGISTERED,
    ACCOUNT_UPDATED,
    ACCOUNT_DELETED,
    ACCOUNT_VERIFIED,
    ACCOUNT_ENABLED,
    ACCOUNT_DISABLED,
    
    // User Events (user-service)
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,

    USER_INDEX_REQUESTED,
    USER_INDEX_DELETED,
    USER_PRIVACY_CHANGED,

    // Friend Events (friend-service)
    FRIENDSHIP_CHANGED
}
