package com.bondhub.aiservice.util;

import org.springframework.util.StringUtils;

public class StringUtil {
    public static String clean(String value) {
        if (value == null || !StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }
}
