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

    // Social Feed Events (social-feed-service)
    POST_CREATED,
    POST_UPDATED,
    POST_DELETED,
    REACTION_TOGGLE_COMMAND_REQUESTED,
    POST_COMMENT_COUNT_PROJECTION_REQUESTED,
    USER_INTERACTION_RECORDED
}
