package com.bondhub.searchservice.enums;

import java.util.Arrays;

public enum MessageSearchSection {
    ALL("all"),
    MESSAGES("messages"),
    FILES("files");

    private final String value;

    MessageSearchSection(String value) {
        this.value = value;
    }

    public static MessageSearchSection fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        return Arrays.stream(values())
                .filter(section -> section.value.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(ALL);
    }

    public String getValue() {
        return value;
    }
}
