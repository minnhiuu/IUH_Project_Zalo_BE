package com.bondhub.common.utils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhoneUtil {
    private static final Pattern VN_PHONE_PATTERN = Pattern.compile("^(\\+84|84|0)?([1-9]\\d{8})$");

    /**
     * Checks if the given string is a valid Vietnamese phone number.
     * Supports: 0xxxxxxxxx, +84xxxxxxxxxx, 84xxxxxxxxxx, xxxxxxxxx (9 digits)
     */
    public static boolean isValidVnPhone(String phone) {
        if (phone == null || phone.isBlank()) return false;
        String cleanPhone = phone.trim().replaceAll("\\s+", "");
        return VN_PHONE_PATTERN.matcher(cleanPhone).matches();
    }

    /**
     * Normalizes a phone number to the standard '0xxxxxxxxx' format used in the system.
     * Returns Optional.empty() if the phone is invalid.
     */
    public static Optional<String> normalizeVnPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        
        String cleanPhone = phone.trim().replaceAll("\\s+", "");
        Matcher matcher = VN_PHONE_PATTERN.matcher(cleanPhone);
        
        if (matcher.matches()) {
            return Optional.of("0" + matcher.group(2));
        }
        
        return Optional.empty();
    }
}
