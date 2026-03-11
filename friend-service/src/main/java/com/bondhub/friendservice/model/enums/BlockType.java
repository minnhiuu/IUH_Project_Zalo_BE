package com.bondhub.friendservice.model.enums;

/**
 * Represents the type of communication channel that can be blocked between users.
 */
public enum BlockType {
    /** Block message communication */
    MESSAGE,
    /** Block call communication */
    CALL,
    /** Block story visibility */
    STORY,
    /** Block all types of communication */
    ALL
}
