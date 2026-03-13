package com.bondhub.userservice.model.enums;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

public enum UserStatus {
    ACTIVE,
    BANNED;

    public static UserStatus fromString(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return UserStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
    }
}
