package com.bondhub.notificationservices.enums;

import com.bondhub.common.enums.NotificationType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BatchWindowConfig {

    // --- BATCHABLE ---
    DOB             (NotificationType.DOB,             300, false),
    FRIEND_REQUEST  (NotificationType.FRIEND_REQUEST,  0,   true),
    POST_LIKE       (NotificationType.POST_LIKE,       30,  true),
    POST_COMMENT    (NotificationType.POST_COMMENT,    10,  true),
    COMMENT_LIKE    (NotificationType.COMMENT_LIKE,    30,  true),
    COMMENT_REPLY   (NotificationType.COMMENT_REPLY,   5,   true),

    // --- NON-BATCHABLE HOẶC URGENT ---
    FRIEND_ACCEPT   (NotificationType.FRIEND_ACCEPT,   0,   false),
    POST_TAG        (NotificationType.POST_TAG,        0,   true),
    POST_MENTION    (NotificationType.POST_MENTION,    2,   true),
    COMMENT_MENTION (NotificationType.COMMENT_MENTION, 2,   true),

    // --- KHÔNG ĐƯỢC BATCH ---
    MESSAGE_DIRECT  (NotificationType.MESSAGE_DIRECT,  0,   false),
    CALL            (NotificationType.CALL,            0,   false),
    SYSTEM          (NotificationType.SYSTEM,          0,   false),
    DLQ_ALERT       (NotificationType.DLQ_ALERT,       30,  true);

    private final NotificationType type;
    private final int windowSeconds;
    private final boolean includeReferenceInKey;

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