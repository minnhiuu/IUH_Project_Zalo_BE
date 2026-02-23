package com.bondhub.notificationservices.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BatchWindowConfig {

    // --- BATCHABLE ---
    DOB             (NotificationType.DOB,             300),
    FRIEND_REQUEST  (NotificationType.FRIEND_REQUEST,  10),
    POST_LIKE       (NotificationType.POST_LIKE,       30),
    POST_COMMENT    (NotificationType.POST_COMMENT,    10),
    COMMENT_LIKE    (NotificationType.COMMENT_LIKE,    30),
    COMMENT_REPLY   (NotificationType.COMMENT_REPLY,   5),

    // --- NON-BATCHABLE HOẶC URGENT ---
    FRIEND_ACCEPT   (NotificationType.FRIEND_ACCEPT,   0),
    POST_TAG        (NotificationType.POST_TAG,        0),
    POST_MENTION    (NotificationType.POST_MENTION,    2),
    COMMENT_MENTION (NotificationType.COMMENT_MENTION, 2),

    // --- KHÔNG ĐƯỢC BATCH ---
    MESSAGE_DIRECT  (NotificationType.MESSAGE_DIRECT,  0),
    CALL            (NotificationType.CALL,            0),
    SYSTEM          (NotificationType.SYSTEM,          0);

    private final NotificationType type;
    private final int windowSeconds;

    public boolean isBatchable() {
        return windowSeconds > 0;
    }

    public static BatchWindowConfig of(NotificationType type) {
        for (BatchWindowConfig cfg : values()) {
            if (cfg.type == type) return cfg;
        }
        throw new IllegalArgumentException("No BatchWindowConfig for: " + type);
    }
}