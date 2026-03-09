package com.bondhub.friendservice.annotation;

import com.bondhub.friendservice.model.enums.BlockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to check if communication is blocked before executing the method
 *
 * Usage:
 * @CheckBlockStatus(blockType = BlockType.MESSAGE, targetUserIdParam = "recipientId")
 *
 * The aspect will check if the target user has blocked the current user
 * for the specified communication type
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckBlockStatus {

    /**
     * Type of communication to check (MESSAGE, CALL, STORY, ALL)
     */
    BlockType blockType() default BlockType.ALL;

    /**
     * Name of the method parameter that contains the target user ID
     * The aspect will extract this parameter value to check blocking status
     */
    String targetUserIdParam();

    /**
     * Whether to check bidirectional blocking (both ways)
     * If true, checks if either user has blocked the other
     */
    boolean bidirectional() default false;
}
